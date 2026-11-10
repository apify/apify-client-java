package com.apify.client.actor;

import com.apify.client.ApifyClient;
import com.apify.client.build.Build;
import com.apify.client.build.BuildClient;
import com.apify.client.build.BuildCollectionClient;
import com.apify.client.http.Timeout;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.Json;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import com.apify.client.internal.RunStartSupport;
import com.apify.client.run.ActorRun;
import com.apify.client.run.LastRunOptions;
import com.apify.client.run.RunClient;
import com.apify.client.run.RunCollectionClient;
import com.apify.client.webhook.NestedWebhookCollectionClient;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * A client for a specific Actor.
 *
 * <p>It provides CRUD methods plus convenience helpers to start/call the Actor, build it, and
 * access its runs, builds, versions and webhooks.
 */
public final class ActorClient {
  private static final Logger LOG = LoggerFactory.getLogger(ActorClient.class);

  /** Set once the deprecation of {@link #standby()} was logged, which is done once per process. */
  private static final AtomicBoolean STANDBY_DEPRECATION_LOGGED = new AtomicBoolean();

  private final ApifyClient root;
  private final HttpClientCore http;
  private final ResourceContext ctx;
  private final String baseUrl;
  private final String id;
  private final ActorServerClient server;

  public ActorClient(ApifyClient root, HttpClientCore http, String baseUrl, String id) {
    this.root = root;
    this.http = http;
    this.ctx = ResourceContext.single(http, baseUrl, ApiPaths.ACTORS, id);
    this.baseUrl = baseUrl;
    this.id = id;
    this.server = new ActorServerClient(http, baseUrl, id);
  }

  /** The Actor's ID (or {@code username~name}) as provided. */
  public String getId() {
    return id;
  }

  /** Fetches the Actor object, or empty if it does not exist. */
  public CompletableFuture<Optional<Actor>> get() {
    return ctx.getResource("", new QueryParams(), Actor.class);
  }

  /** Updates the Actor with the given fields and returns the updated object. */
  public CompletableFuture<Actor> update(Object newFields) {
    return ctx.updateResource("", newFields, Actor.class);
  }

  /** Deletes the Actor. */
  public CompletableFuture<Void> delete() {
    return ctx.deleteResource("");
  }

  /**
   * Starts the Actor and completes with the created run as soon as it exists (no waiting). {@code
   * input} is any JSON-serializable value, a {@code byte[]} or {@link String} that is sent as it is
   * (set the content type with the options' {@code contentType}), or {@code null} for no input.
   */
  public CompletableFuture<ActorRun> start(Object input, ActorStartOptions options) {
    return RunStartSupport.start(
        ctx,
        input,
        options::apply,
        options.contentTypeOrDefault(),
        options.waitForResourcesSecsValue());
  }

  /**
   * Starts the Actor and waits (non-blocking, polling) for it to finish. {@code waitSecs} bounds
   * the wait; {@code null} waits indefinitely. Completes with the finished run (or the
   * still-running run if the wait budget was exhausted).
   *
   * <p>This overload does not stream the run's log; use {@link #call(Object, ActorCallOptions,
   * Long)} for that.
   */
  public CompletableFuture<ActorRun> call(Object input, ActorStartOptions options, Long waitSecs) {
    return RunStartSupport.call(
        root,
        ctx,
        input,
        options::apply,
        options.contentTypeOrDefault(),
        waitSecs,
        options.waitForResourcesSecsValue());
  }

  /**
   * Starts the Actor and waits (non-blocking, polling) for it to finish, additionally streaming the
   * run's log for the duration of the wait. {@code waitSecs} bounds the wait; {@code null} waits
   * indefinitely. Completes with the finished run (or the still-running run if the wait budget was
   * exhausted).
   *
   * <p>Log streaming is best-effort: if starting it fails (e.g. the log is not yet available), the
   * run still starts and is still waited for, just without redirected log output. Use {@link
   * ActorCallOptions#disableLogStreaming()} to opt out entirely, or {@link
   * ActorCallOptions#logOptions(com.apify.client.log.StreamedLogOptions)} for a custom destination.
   */
  public CompletableFuture<ActorRun> call(Object input, ActorCallOptions options, Long waitSecs) {
    ActorCallOptions opts = options != null ? options : new ActorCallOptions();
    ActorStartOptions startOptions = opts.toStartOptions();
    return RunStartSupport.callWithLogStreaming(
        root,
        ctx,
        input,
        startOptions::apply,
        startOptions.contentTypeOrDefault(),
        opts.logStreamingEnabledValue(),
        opts.logOptionsValue(),
        waitSecs,
        startOptions.waitForResourcesSecsValue());
  }

  /** Validates the given input against the Actor's default-build input schema. */
  public CompletableFuture<Boolean> validateInput(Object input) {
    return validateInput(input, new ValidateInputOptions());
  }

  /**
   * Validates {@code input} against the Actor's input schema and completes with whether it is
   * valid. {@code input} is any JSON-serializable value, a {@code byte[]} or {@link String} that is
   * sent as it is, or {@code null}. {@code options} may pin the build whose schema is used and the
   * content type of the input body.
   */
  public CompletableFuture<Boolean> validateInput(Object input, ValidateInputOptions options) {
    ValidateInputOptions opts = options == null ? new ValidateInputOptions() : options;
    QueryParams params = new QueryParams();
    opts.apply(params);
    byte[] body = Json.toRequestBody(input);
    // The validate-input endpoint returns a bare {"valid": <bool>} object, not the standard
    // {"data": ...} envelope, so parse it without unwrapping.
    return ctx.postWithBodyNoEnvelope(
            "validate-input",
            params,
            body,
            opts.contentTypeOrDefault(),
            JsonNode.class,
            Timeout.SHORT)
        .thenApply(
            result -> {
              JsonNode valid = result == null ? null : result.get("valid");
              return valid != null && valid.asBoolean();
            });
  }

  /** Builds the given version of the Actor and completes with the created build. */
  public CompletableFuture<Build> build(String versionNumber, ActorBuildOptions options) {
    if (options == null) {
      throw new IllegalArgumentException("options is required and must not be null");
    }
    QueryParams params = new QueryParams();
    params.addString("version", versionNumber);
    options.apply(params);
    HttpClientCore.WaitHold hold =
        ctx.http.waitHold(Timeout.MEDIUM, params.getLong("waitForFinish"));
    params.setLong("waitForFinish", hold.waitForFinishSecs());
    return ctx.postWithBody(
        "builds", params, null, ResourceContext.CONTENT_TYPE_JSON, Build.class, hold.timeout());
  }

  /**
   * Resolves the Actor's default build and completes with a client for it. {@code waitForFinish}
   * optionally bounds how long (in seconds) the API waits for the build to finish before
   * responding.
   */
  public CompletableFuture<BuildClient> defaultBuild(Long waitForFinish) {
    QueryParams params = new QueryParams();
    HttpClientCore.WaitHold hold = ctx.http.waitHold(Timeout.SHORT, waitForFinish);
    params.addLong("waitForFinish", hold.waitForFinishSecs());
    return ctx.getResourceRequired("builds/default", params, Build.class, hold.timeout())
        .thenApply(build -> new BuildClient(http, baseUrl, build.getId()));
  }

  /**
   * Returns a client for the last run of this Actor, optionally filtered by status (e.g. {@code
   * "SUCCEEDED"}). Pass {@code null} or empty for no filter.
   */
  public RunClient lastRun(String status) {
    return lastRun(new LastRunOptions().status(status));
  }

  /**
   * Returns a client for the last run of this Actor, optionally filtered by status and/or origin.
   */
  public RunClient lastRun(LastRunOptions options) {
    return RunClient.lastRun(root, http, ctx.subUrl(""), options);
  }

  /**
   * Returns a client for the server interface of this Actor.
   *
   * <p>A Server Actor runs a web server, its server interface, that is called over HTTP without
   * starting a run for each call. Server Actors were formerly called Actor Standby, and {@link
   * #standby()} is a deprecated alias of this method. Every call returns the same client, so the
   * server URL it fetches is reused.
   *
   * @see <a href="https://docs.apify.com/platform/actors/running/standby">Standby mode</a>
   */
  public ActorServerClient server() {
    return server;
  }

  /**
   * Returns a client for the server interface of this Actor in Standby mode, named after Actor
   * Standby, the former name of Server Actors.
   *
   * @deprecated Use {@link #server()} instead. Will be removed in a future major version.
   */
  @Deprecated
  public ActorServerClient standby() {
    if (STANDBY_DEPRECATION_LOGGED.compareAndSet(false, true)) {
      LOG.warn(
          "`standby()` is deprecated and will be removed in a future major version. Use"
              + " `server()` instead.");
    }
    return server();
  }

  /** A client for this Actor's build collection. */
  public BuildCollectionClient builds() {
    return new BuildCollectionClient(http, ctx.subUrl(""), "builds");
  }

  /** A client for this Actor's run collection. */
  public RunCollectionClient runs() {
    return new RunCollectionClient(http, ctx.subUrl(""), "runs");
  }

  /** A client for a specific version of this Actor. */
  public ActorVersionClient version(String versionNumber) {
    return new ActorVersionClient(http, ctx.subUrl(""), versionNumber);
  }

  /** A client for this Actor's version collection. */
  public ActorVersionCollectionClient versions() {
    return new ActorVersionCollectionClient(http, ctx.subUrl(""));
  }

  /**
   * A read-only client for this Actor's webhook collection ({@code GET /v2/actors/{id}/webhooks}).
   */
  public NestedWebhookCollectionClient webhooks() {
    return new NestedWebhookCollectionClient(http, ctx.subUrl(""));
  }
}
