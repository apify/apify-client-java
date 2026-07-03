package com.apify.client;

import java.util.Optional;

/**
 * A client for a specific Actor.
 *
 * <p>It provides CRUD methods plus convenience helpers to start/call the Actor, build it, and
 * access its runs, builds, versions and webhooks.
 */
public final class ActorClient {
  private final ApifyClient root;
  private final HttpClientCore http;
  private final ResourceContext ctx;
  private final String baseUrl;
  private final String id;

  ActorClient(ApifyClient root, HttpClientCore http, String baseUrl, String id) {
    this.root = root;
    this.http = http;
    this.ctx = ResourceContext.single(http, baseUrl, "actors", id);
    this.baseUrl = baseUrl;
    this.id = id;
  }

  /** The Actor's ID (or {@code username~name}) as provided. */
  public String getId() {
    return id;
  }

  /** Fetches the Actor object, or empty if it does not exist. */
  public Optional<Actor> get() {
    return ctx.getResource("", new QueryParams(), Actor.class);
  }

  /** Updates the Actor with the given fields and returns the updated object. */
  public Actor update(Object newFields) {
    return ctx.updateResource("", newFields, Actor.class);
  }

  /** Deletes the Actor. */
  public void delete() {
    ctx.deleteResource("");
  }

  /**
   * Starts the Actor and returns immediately with the created run. {@code input} is any
   * JSON-serializable value (or {@code null} for no input).
   */
  public ActorRun start(Object input, ActorStartOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    byte[] body = input == null ? null : Json.toBytes(input);
    return ctx.postWithBody("runs", params, body, options.contentTypeOrDefault(), ActorRun.class);
  }

  /**
   * Starts the Actor and waits (client-side polling) for it to finish. {@code waitSecs} bounds the
   * wait; {@code null} waits indefinitely. Returns the finished run (or the still-running run if
   * the wait budget was exhausted).
   */
  public ActorRun call(Object input, ActorStartOptions options, Long waitSecs) {
    ActorRun run = start(input, options);
    return root.run(run.getId()).waitForFinish(waitSecs);
  }

  /** Builds the given version of the Actor and returns the created build. */
  public Build build(String versionNumber, ActorBuildOptions options) {
    QueryParams params = new QueryParams();
    params.addString("version", versionNumber);
    options.apply(params);
    return ctx.postWithBody("builds", params, null, ResourceContext.CONTENT_TYPE_JSON, Build.class);
  }

  /**
   * Resolves the Actor's default build and returns a client for it. {@code waitForFinish}
   * optionally bounds how long (in seconds) the API waits for the build to finish before
   * responding.
   */
  public BuildClient defaultBuild(Long waitForFinish) {
    QueryParams params = new QueryParams();
    params.addLong("waitForFinish", waitForFinish);
    Build build = ctx.getResourceRequired("builds/default", params, Build.class);
    return new BuildClient(http, baseUrl, build.getId());
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
    RunClient client = new RunClient(root, http, ctx.subUrl(""), "runs", "last");
    client.setLastRunParams(options);
    return client;
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
