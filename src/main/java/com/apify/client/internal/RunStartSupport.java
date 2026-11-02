package com.apify.client.internal;

import com.apify.client.ApifyClient;
import com.apify.client.actor.ActorStartOptions;
import com.apify.client.http.ApifyApiException;
import com.apify.client.http.Timeout;
import com.apify.client.log.StreamedLog;
import com.apify.client.log.StreamedLogOptions;
import com.apify.client.run.ActorRun;
import com.apify.client.run.RunClient;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared implementation of "start a run" and "call: start, then wait, optionally streaming the log"
 * for every client that can start a run from a runnable resource (Actor, Task).
 *
 * <p>{@code ActorStartOptions}/{@code TaskStartOptions}/{@code ActorCallOptions}/{@code
 * TaskCallOptions} (in {@code com.apify.client.actor}/{@code com.apify.client.task}) are themselves
 * public classes — only the handful of accessor methods this class needs from them ({@code apply},
 * {@code contentTypeOrDefault}, {@code waitForResourcesSecsValue}, {@code toStartOptions}, {@code
 * logStreamingEnabledValue}, {@code logOptionsValue}) are package-private, with no public interface
 * or shared supertype exposing them. {@code ActorClient}/{@code TaskClient} (same package as their
 * respective options types) read those values directly and pass them here as plain values and
 * method references (e.g. {@code options::apply} bound as a {@code Consumer<QueryParams>}). That
 * keeps this one implementation backing both Actor and task calls (instead of each duplicating the
 * same ~90 lines) without forcing any of the options builders' internal plumbing — including the
 * internal {@link QueryParams} type — into their public API.
 */
public final class RunStartSupport {

  private static final Logger LOG = LoggerFactory.getLogger(RunStartSupport.class);

  /**
   * API error types the run-start endpoint returns while the account lacks the resources for the
   * run (free memory or a concurrent-run slot). Both clear as other runs or builds finish, which is
   * what makes retrying worthwhile.
   */
  private static final Set<String> RESOURCE_LIMIT_ERROR_TYPES =
      Set.of("actor-memory-limit-exceeded", "concurrent-runs-limit-exceeded");

  /** Delay between two attempts to start a run rejected for lack of resources. */
  private static final Duration WAIT_FOR_RESOURCES_RETRY_INTERVAL = Duration.ofSeconds(10);

  /**
   * Upper bound (seconds) a caller-supplied {@code waitForResourcesSecs} is clamped to before
   * computing a deadline, so a value near {@link Long#MAX_VALUE} cannot overflow the millisecond
   * deadline computation below - the same reason {@code ResourceContext.waitForFinish} clamps its
   * own wait budget before converting it to milliseconds.
   */
  private static final long MAX_WAIT_FOR_RESOURCES_SECS = 999_999;

  private RunStartSupport() {}

  /**
   * Starts a run on {@code ctx}'s {@code runs} sub-resource and completes with the created run as
   * soon as it exists (no waiting). {@code input} is any JSON-serializable value, a {@code byte[]}
   * or {@link String} sent as it is, or {@code null} for no input. {@code applyParams} applies
   * every start-option query parameter (build, memory, timeout, ...) the caller's options type
   * configures; {@code contentType} is the input body's content type.
   *
   * @param waitForResourcesSecs mirrors {@link ActorStartOptions#waitForResources(Long)}: {@code
   *     null} does not retry a start rejected for lack of resources (the default); {@link
   *     ActorStartOptions#WAIT_FOR_RESOURCES_INDEFINITELY} retries until it succeeds; any other
   *     value bounds the retrying to that many seconds, after which the last such error is thrown
   */
  public static CompletableFuture<ActorRun> start(
      ResourceContext ctx,
      Object input,
      Consumer<QueryParams> applyParams,
      String contentType,
      Long waitForResourcesSecs) {
    return start(ctx, input, applyParams, contentType, waitForResourcesSecs, null);
  }

  /**
   * As {@link #start(ResourceContext, Object, Consumer, String, Long)}, with an explicit request
   * timeout; {@code null} picks the {@link Timeout#MEDIUM} tier extended by the start's {@code
   * waitForFinish} hold, so the client does not abort a request the API is still holding.
   */
  private static CompletableFuture<ActorRun> start(
      ResourceContext ctx,
      Object input,
      Consumer<QueryParams> applyParams,
      String contentType,
      Long waitForResourcesSecs,
      Timeout timeout) {
    QueryParams params = new QueryParams();
    applyParams.accept(params);
    byte[] body = Json.toRequestBody(input);
    Timeout requestTimeout = timeout;
    if (requestTimeout == null) {
      HttpClientCore.WaitHold hold =
          ctx.http.waitHold(Timeout.MEDIUM, params.getLong("waitForFinish"));
      params.setLong("waitForFinish", hold.waitForFinishSecs());
      requestTimeout = hold.timeout();
    }
    if (waitForResourcesSecs == null) {
      return ctx.postWithBody("runs", params, body, contentType, ActorRun.class, requestTimeout);
    }
    long deadlineMillis =
        waitForResourcesSecs == ActorStartOptions.WAIT_FOR_RESOURCES_INDEFINITELY
            ? Long.MAX_VALUE
            : System.currentTimeMillis()
                + Math.min(Math.max(0, waitForResourcesSecs), MAX_WAIT_FOR_RESOURCES_SECS) * 1000L;
    return startRetryingForResources(
        ctx, params, body, contentType, requestTimeout, deadlineMillis);
  }

  /**
   * One attempt of the {@code waitForResources} retry loop; recurses (via a non-blocking delay)
   * while the start keeps failing with a {@link #RESOURCE_LIMIT_ERROR_TYPES resource-limit error}
   * and the deadline has not passed. Any other error, or a resource-limit error once the deadline
   * has passed, is rethrown as-is.
   */
  private static CompletableFuture<ActorRun> startRetryingForResources(
      ResourceContext ctx,
      QueryParams params,
      byte[] body,
      String contentType,
      Timeout timeout,
      long deadlineMillis) {
    return ctx.postWithBody("runs", params, body, contentType, ActorRun.class, timeout)
        .handle(
            (run, error) -> {
              if (error == null) {
                return CompletableFuture.completedFuture(run);
              }
              Throwable cause = HttpClientCore.unwrapCompletion(error);
              if (!(cause instanceof ApifyApiException apiError)
                  || !RESOURCE_LIMIT_ERROR_TYPES.contains(apiError.getType())) {
                return CompletableFuture.<ActorRun>failedFuture(cause);
              }
              long remainingMillis = deadlineMillis - System.currentTimeMillis();
              if (remainingMillis <= 0) {
                return CompletableFuture.<ActorRun>failedFuture(cause);
              }
              Duration delay =
                  Duration.ofMillis(
                      Math.min(WAIT_FOR_RESOURCES_RETRY_INTERVAL.toMillis(), remainingMillis));
              LOG.info(
                  "Not enough resources to start the run, retrying in {}s: {}",
                  delay.toSeconds(),
                  apiError.getMessage());
              return Async.delay(delay)
                  .thenCompose(
                      unused ->
                          startRetryingForResources(
                              ctx, params, body, contentType, timeout, deadlineMillis));
            })
        .thenCompose(future -> future);
  }

  /**
   * Starts a run and waits (non-blocking, polling) for it to finish, without log streaming.
   *
   * @param waitSecs bounds the wait; {@code null} waits indefinitely
   * @param waitForResourcesSecs see {@link #start}
   */
  public static CompletableFuture<ActorRun> call(
      ApifyClient root,
      ResourceContext ctx,
      Object input,
      Consumer<QueryParams> applyParams,
      String contentType,
      Long waitSecs,
      Long waitForResourcesSecs) {
    return start(ctx, input, applyParams, contentType, waitForResourcesSecs, Timeout.NO_TIMEOUT)
        .thenCompose(run -> root.run(run.getId()).waitForFinish(waitSecs));
  }

  /**
   * Starts a run and waits for it to finish, additionally streaming the run's log for the duration
   * of the wait by default, matching the reference client's {@code call} defaulting {@code
   * options.log} to {@code 'default'}. Log streaming is best-effort: if starting it fails (e.g. the
   * log is not yet available), the run still starts and is still waited for, just without
   * redirected log output.
   *
   * @param applyParams applies every start-option query parameter the caller's call-options type
   *     configures (delegated from its paired start-options type)
   * @param contentType the input body's content type
   * @param logStreamingEnabled whether to stream the log for the duration of the wait
   * @param logOptions a custom log-streaming destination, or {@code null} for the default
   * @param waitSecs bounds the wait; {@code null} waits indefinitely
   * @param waitForResourcesSecs see {@link #start}
   */
  public static CompletableFuture<ActorRun> callWithLogStreaming(
      ApifyClient root,
      ResourceContext ctx,
      Object input,
      Consumer<QueryParams> applyParams,
      String contentType,
      boolean logStreamingEnabled,
      StreamedLogOptions logOptions,
      Long waitSecs,
      Long waitForResourcesSecs) {
    AtomicReference<StreamedLog> opened = new AtomicReference<>();
    AtomicBoolean finished = new AtomicBoolean();
    CompletableFuture<ActorRun> result =
        start(ctx, input, applyParams, contentType, waitForResourcesSecs, Timeout.NO_TIMEOUT)
            .thenCompose(
                run -> {
                  RunClient runClient = root.run(run.getId());
                  CompletableFuture<?> logStarted =
                      logStreamingEnabled
                          ? startStreamedLogQuietly(runClient, logOptions, opened, finished)
                          : CancellableFuture.completed(null);
                  return logStarted.thenCompose(unused -> runClient.waitForFinish(waitSecs));
                });
    // Closed from a callback of the returned future itself, which runs whether the call finishes,
    // fails or is cancelled (a stage that is cancelled never runs its own callbacks).
    result.whenComplete(
        (run, error) -> {
          finished.set(true);
          StreamedLog streamedLog = opened.get();
          if (streamedLog != null) {
            streamedLog.close();
          }
        });
    return result;
  }

  /**
   * Starts {@code call}'s default log streaming, swallowing (rather than propagating) any failure
   * to open the live log stream, so a transient log-endpoint issue cannot abort the run itself. The
   * {@link StreamedLog} is handed to {@code opened} as soon as it exists, before it is started, so
   * a call that ends while the stream is still opening can close it; one that has already ended
   * ({@code finished}) closes it right away.
   */
  private static CompletableFuture<Void> startStreamedLogQuietly(
      RunClient runClient,
      StreamedLogOptions logOptions,
      AtomicReference<StreamedLog> opened,
      AtomicBoolean finished) {
    CompletableFuture<StreamedLog> created =
        logOptions != null ? runClient.getStreamedLog(logOptions) : runClient.getStreamedLog();
    return created
        .thenCompose(
            streamedLog -> {
              opened.set(streamedLog);
              if (finished.get()) {
                streamedLog.close();
                return CancellableFuture.<Void>completed(null);
              }
              CompletableFuture<Void> started = streamedLog.start();
              if (finished.get()) {
                // The call ended between the check above and the start: close() while the stream
                // is opening makes it launch no reader.
                streamedLog.close();
              }
              return started;
            })
        .handle((unused, error) -> (Void) null);
  }
}
