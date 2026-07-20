package com.apify.client.run;

import com.apify.client.ApifyClient;
import com.apify.client.QueryParams;
import com.apify.client.ResourceContext;
import com.apify.client.actor.Actor;
import com.apify.client.dataset.DatasetClient;
import com.apify.client.http.HttpClientCore;
import com.apify.client.http.Json;
import com.apify.client.keyvalue.KeyValueStoreClient;
import com.apify.client.log.LogClient;
import com.apify.client.log.StreamedLog;
import com.apify.client.log.StreamedLogOptions;
import com.apify.client.requestqueue.RequestQueueClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client for a specific Actor run.
 *
 * <p>It provides CRUD methods plus convenience helpers (abort, metamorph, reboot, resurrect,
 * charge, wait-for-finish) and accessors for the run's default storages and log.
 */
public final class RunClient {

  /** Header the API uses to deduplicate charge requests. */
  private static final String CHARGE_IDEMPOTENCY_HEADER = "idempotency-key";

  private final ApifyClient root;
  private final ResourceContext ctx;
  private final String id;

  public RunClient(
      ApifyClient root, HttpClientCore http, String baseUrl, String resourcePath, String id) {
    this.root = root;
    this.ctx = ResourceContext.single(http, baseUrl, resourcePath, id);
    this.id = id;
  }

  /**
   * Pins the {@code status} and/or {@code origin} query parameters inherited by all calls on this
   * client (used by the last-run accessors). Empty values are skipped.
   */
  public void setLastRunParams(LastRunOptions options) {
    if (options.statusValue() != null && !options.statusValue().isEmpty()) {
      ctx.baseParams.addRaw("status", options.statusValue());
    }
    if (options.originValue() != null && !options.originValue().isEmpty()) {
      ctx.baseParams.addRaw("origin", options.originValue());
    }
  }

  /** Fetches the run object, or empty if it does not exist. */
  public Optional<ActorRun> get() {
    return getWithWait(null);
  }

  /**
   * Fetches the run, optionally asking the API to wait up to {@code waitForFinishSecs} seconds for
   * the run to reach a terminal state before responding. The value is clamped so the server always
   * responds before the client's per-request timeout; the API itself caps server-side waiting at 60
   * seconds. Pass {@code null} for an immediate fetch.
   */
  public Optional<ActorRun> getWithWait(Long waitForFinishSecs) {
    QueryParams params = new QueryParams();
    // Clamp to the client's per-request timeout so a short custom timeout doesn't abort the call.
    params.addLong("waitForFinish", ctx.clampServerWait(waitForFinishSecs));
    return ctx.getResource("", params, ActorRun.class);
  }

  /** Updates the run with the given fields and returns the updated object. */
  public ActorRun update(Object newFields) {
    return ctx.updateResource("", newFields, ActorRun.class);
  }

  /** Deletes the run. */
  public void delete() {
    ctx.deleteResource("");
  }

  /**
   * Aborts the run. If {@code gracefully} is {@code true}, the run is signalled so it can finish
   * the current request before terminating; if {@code false} it is aborted immediately. Pass {@code
   * null} to omit the parameter and let the server apply its default (immediate abort).
   */
  public ActorRun abort(Boolean gracefully) {
    QueryParams params = new QueryParams();
    params.addBool("gracefully", gracefully);
    return ctx.postWithBody("abort", params, null, "", ActorRun.class);
  }

  /**
   * Transforms the run into a run of another Actor with a new input. {@code targetActorId} is the
   * Actor to metamorph into; {@code input} is the new input ({@code null} for none).
   */
  public ActorRun metamorph(String targetActorId, Object input, MetamorphOptions options) {
    QueryParams params = new QueryParams();
    params.addString("targetActorId", targetActorId);
    if (options.buildValue() != null && !options.buildValue().isEmpty()) {
      params.addString("build", options.buildValue());
    }
    byte[] body = input == null ? null : Json.toBytes(input);
    return ctx.postWithBody(
        "metamorph", params, body, options.contentTypeOrDefault(), ActorRun.class);
  }

  /** Reboots the run (restarts its container while keeping the same run). */
  public ActorRun reboot() {
    return ctx.postWithBody("reboot", new QueryParams(), null, "", ActorRun.class);
  }

  /** Resurrects a finished run, starting it again from the beginning. */
  public ActorRun resurrect(RunResurrectOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.postWithBody("resurrect", params, null, "", ActorRun.class);
  }

  /**
   * Charges for a pay-per-event Actor run: records occurrences of a named event. Only meaningful
   * for runs of pay-per-event Actors.
   *
   * <p>An idempotency key is always sent (auto-generated if not provided), so a charge that is
   * retried by the transport is applied at most once, matching the reference client.
   */
  public void charge(RunChargeOptions options) {
    String eventName = options.eventNameValue();
    if (eventName == null || eventName.isEmpty()) {
      throw new IllegalArgumentException(
          "RunChargeOptions.eventName is required and must not be empty");
    }
    String idempotencyKey = options.idempotencyKeyValue();
    if (idempotencyKey == null || idempotencyKey.isEmpty()) {
      idempotencyKey = generateIdempotencyKey(eventName);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("eventName", eventName);
    body.put("count", options.countValue());
    Map<String, String> headers = Map.of(CHARGE_IDEMPOTENCY_HEADER, idempotencyKey);
    // Route through mergedParams like the run's other actions, so a last-run-seeded context charges
    // the same run its read methods resolve to (its pinned status/origin filters are preserved).
    String url = ctx.mergedParams(new QueryParams()).applyToUrl(ctx.subUrl("charge"));
    ctx.http.call(
        "POST",
        url,
        Json.toBytes(body),
        ResourceContext.CONTENT_TYPE_JSON,
        headers,
        ctx.http.baseRequestTimeout());
  }

  /**
   * Builds a per-charge idempotency key of the form {@code
   * "{runId}-{eventName}-{timestampMillis}-{random}"}. It need not be cryptographically secure,
   * only unique enough to avoid collisions within a request.
   */
  private String generateIdempotencyKey(String eventName) {
    return id
        + "-"
        + eventName
        + "-"
        + System.currentTimeMillis()
        + "-"
        + ThreadLocalRandom.current().nextLong(1_000_000);
  }

  /**
   * Polls until the run reaches a terminal state or {@code waitSecs} elapses ({@code null} waits
   * indefinitely). Returns the latest run.
   */
  public ActorRun waitForFinish(Long waitSecs) {
    return ctx.waitForFinish(waitSecs, "run", Json.type(ActorRun.class), ActorRun::isTerminal);
  }

  /** A client for this run's default dataset. */
  public DatasetClient dataset() {
    return DatasetClient.nested(ctx.http, ctx.subUrl(""), "dataset", ctx.baseParams);
  }

  /** A client for this run's default key-value store. */
  public KeyValueStoreClient keyValueStore() {
    return KeyValueStoreClient.nested(ctx.http, ctx.subUrl(""), "key-value-store", ctx.baseParams);
  }

  /** A client for this run's default request queue. */
  public RequestQueueClient requestQueue() {
    return RequestQueueClient.nested(ctx.http, ctx.subUrl(""), "request-queue", ctx.baseParams);
  }

  /** A client for accessing this run's log. */
  public LogClient log() {
    return LogClient.nested(ctx.http, ctx.subUrl(""), ctx.baseParams);
  }

  /** Logger name used by the default log-redirection destination. */
  private static final String REDIRECT_LOGGER_NAME = "com.apify.client.ActorRunLog";

  /**
   * Returns a {@link StreamedLog} that redirects this run's live log to a default per-run SLF4J
   * logger.
   *
   * <p>The default destination is a {@link Logger} that receives each message at {@code INFO}
   * level, prefixed with the Actor name and run id (built by fetching the run and its Actor). Call
   * {@link StreamedLog#start()} to begin redirection and {@link StreamedLog#stop()} (or {@link
   * StreamedLog#close()}) to end it.
   *
   * <p>For a custom destination or to skip historical log lines, use {@link
   * #getStreamedLog(StreamedLogOptions)}. For raw stream access without redirection, use {@link
   * #log()}{@code .stream(...)}.
   */
  public StreamedLog getStreamedLog() {
    return getStreamedLog(new StreamedLogOptions());
  }

  /**
   * Returns a {@link StreamedLog} that redirects this run's live log according to {@code options}.
   *
   * <p>If {@link StreamedLogOptions#toLog(Consumer)} is set, each complete log message is passed to
   * that consumer; otherwise a default {@link Logger} destination is used with a per-run prefix (an
   * explicit {@link StreamedLogOptions#prefix(String)} overrides the auto-built one). {@link
   * StreamedLogOptions#fromStart(boolean)} controls whether log lines produced before redirection
   * started are included.
   */
  public StreamedLog getStreamedLog(StreamedLogOptions options) {
    Consumer<String> destination = options.destination();
    if (destination == null) {
      String prefix =
          options.prefixValue() != null ? options.prefixValue() : buildDefaultLogPrefix();
      destination = defaultLogDestination(prefix);
    }
    return new StreamedLog(log(), destination, options.fromStartValue());
  }

  /**
   * Builds the per-run prefix used by the default redirection destination, mirroring the reference
   * client: the Actor name (looked up from the run) and {@code runId:{id}}, joined with a space and
   * followed by {@code " -> "}. Falls back to just the run id when the Actor name is unavailable.
   */
  private String buildDefaultLogPrefix() {
    String runPart = "runId:" + id;
    String actorName = "";
    try {
      Optional<ActorRun> run = get();
      if (run.isPresent() && run.get().getActId() != null && !run.get().getActId().isEmpty()) {
        Optional<Actor> actor = root.actor(run.get().getActId()).get();
        if (actor.isPresent() && actor.get().getName() != null) {
          actorName = actor.get().getName();
        }
      }
    } catch (RuntimeException e) {
      // The Actor-name lookup is cosmetic. The getters swallow 404, but an auth (401/403),
      // transport, or 5xx-after-retries failure would otherwise throw out of getStreamedLog() and
      // abort helper creation even though streaming itself might have worked. Fall back to the
      // runId-only prefix (actorName stays "") so the helper is always created.
    }
    String name = actorName.isEmpty() ? runPart : actorName + " " + runPart;
    return name + " -> ";
  }

  /** A destination that logs each message at {@code INFO} level, prefixed with {@code prefix}. */
  private static Consumer<String> defaultLogDestination(String prefix) {
    Logger logger = LoggerFactory.getLogger(REDIRECT_LOGGER_NAME);
    return message -> logger.info(prefix + message);
  }
}
