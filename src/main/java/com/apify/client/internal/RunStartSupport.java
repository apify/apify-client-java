package com.apify.client.internal;

import com.apify.client.ApifyClient;
import com.apify.client.log.StreamedLog;
import com.apify.client.log.StreamedLogOptions;
import com.apify.client.run.ActorRun;
import com.apify.client.run.RunClient;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Shared implementation of "start a run" and "call: start, then wait, optionally streaming the log"
 * for every client that can start a run from a runnable resource (Actor, Task).
 *
 * <p>{@code ActorStartOptions}/{@code TaskStartOptions}/{@code ActorCallOptions}/{@code
 * TaskCallOptions} (in {@code com.apify.client.actor}/{@code com.apify.client.task}) are themselves
 * public classes — only the handful of accessor methods this class needs from them ({@code apply},
 * {@code contentTypeOrDefault}, {@code toStartOptions}, {@code logStreamingEnabledValue}, {@code
 * logOptionsValue}) are package-private, with no public interface or shared supertype exposing
 * them. {@code ActorClient}/{@code TaskClient} (same package as their respective options types)
 * read those values directly and pass them here as plain values and method references (e.g. {@code
 * options::apply} bound as a {@code Consumer<QueryParams>}). That keeps this one implementation
 * backing both Actor and task calls (instead of each duplicating the same ~90 lines) without
 * forcing any of the options builders' internal plumbing — including the internal {@link
 * QueryParams} type — into their public API.
 */
public final class RunStartSupport {

  private RunStartSupport() {}

  /**
   * Starts a run on {@code ctx}'s {@code runs} sub-resource and completes with the created run as
   * soon as it exists (no waiting). {@code input} is any JSON-serializable value, or {@code null}
   * for no input. {@code applyParams} applies every start-option query parameter (build, memory,
   * timeout, ...) the caller's options type configures; {@code contentType} is the input body's
   * content type.
   */
  public static CompletableFuture<ActorRun> start(
      ResourceContext ctx, Object input, Consumer<QueryParams> applyParams, String contentType) {
    QueryParams params = new QueryParams();
    applyParams.accept(params);
    byte[] body = input == null ? null : Json.toBytes(input);
    return ctx.postWithBody("runs", params, body, contentType, ActorRun.class);
  }

  /**
   * Starts a run and waits (non-blocking, polling) for it to finish, without log streaming.
   *
   * @param waitSecs bounds the wait; {@code null} waits indefinitely
   */
  public static CompletableFuture<ActorRun> call(
      ApifyClient root,
      ResourceContext ctx,
      Object input,
      Consumer<QueryParams> applyParams,
      String contentType,
      Long waitSecs) {
    return start(ctx, input, applyParams, contentType)
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
   */
  public static CompletableFuture<ActorRun> callWithLogStreaming(
      ApifyClient root,
      ResourceContext ctx,
      Object input,
      Consumer<QueryParams> applyParams,
      String contentType,
      boolean logStreamingEnabled,
      StreamedLogOptions logOptions,
      Long waitSecs) {
    return start(ctx, input, applyParams, contentType)
        .thenCompose(
            run -> {
              RunClient runClient = root.run(run.getId());
              CompletableFuture<StreamedLog> streamedLogFuture =
                  logStreamingEnabled
                      ? startStreamedLogQuietly(runClient, logOptions)
                      : CompletableFuture.completedFuture(null);
              return streamedLogFuture.thenCompose(
                  streamedLog ->
                      runClient
                          .waitForFinish(waitSecs)
                          .whenComplete(
                              (result, error) -> {
                                if (streamedLog != null) {
                                  streamedLog.close();
                                }
                              }));
            });
  }

  /**
   * Starts {@code call}'s default log streaming, swallowing (rather than propagating) any failure
   * to open the live log stream, so a transient log-endpoint issue cannot abort the run itself.
   * Completes with the started {@link StreamedLog}, or {@code null} if it could not be started.
   */
  private static CompletableFuture<StreamedLog> startStreamedLogQuietly(
      RunClient runClient, StreamedLogOptions logOptions) {
    CompletableFuture<StreamedLog> created =
        logOptions != null ? runClient.getStreamedLog(logOptions) : runClient.getStreamedLog();
    return created
        .thenCompose(
            streamedLog ->
                streamedLog.start().handle((unused, error) -> error == null ? streamedLog : null))
        .exceptionally(error -> null);
  }
}
