package com.apify.client.task;

import com.apify.client.ApifyClient;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import tools.jackson.databind.JsonNode;

/**
 * A client for a specific Actor task.
 *
 * <p>Tasks are pre-configured Actor runs with stored input. The client provides CRUD methods plus
 * convenience helpers to start/call the task and access its input, runs and webhooks.
 */
public final class TaskClient {
  private final ApifyClient root;
  private final HttpClientCore http;
  private final ResourceContext ctx;

  public TaskClient(ApifyClient root, HttpClientCore http, String baseUrl, String id) {
    this.root = root;
    this.http = http;
    this.ctx = ResourceContext.single(http, baseUrl, ApiPaths.ACTOR_TASKS, id);
  }

  /** Fetches the task object, or empty if it does not exist. */
  public CompletableFuture<Optional<Task>> get() {
    return ctx.getResource("", new QueryParams(), Task.class);
  }

  /** Updates the task with the given fields and returns the updated object. */
  public CompletableFuture<Task> update(Object newFields) {
    return ctx.updateResource("", newFields, Task.class);
  }

  /** Deletes the task. */
  public CompletableFuture<Void> delete() {
    return ctx.deleteResource("");
  }

  /**
   * Publishes the task on its public landing page, by setting {@code isPublic} through {@link
   * #update(Object)}.
   *
   * <p>The task's Actor must be public and the task must have its public display configuration
   * ({@code publicConfig}) set up first. Requires write permission to both the task and its Actor.
   * Publishing an already published task does nothing.
   */
  public CompletableFuture<Task> publish() {
    return update(Map.of("isPublic", true));
  }

  /**
   * Unpublishes the task from its public landing page, by setting {@code isPublic} through {@link
   * #update(Object)}.
   *
   * <p>The public display configuration ({@code publicConfig}) is preserved, so the task can be
   * published again without re-entering it. Requires write permission to the task only (unlike
   * {@link #publish()}, it does not require permission to the task's Actor). Unpublishing a task
   * that is not published does nothing.
   */
  public CompletableFuture<Task> unpublish() {
    return update(Map.of("isPublic", false));
  }

  /**
   * Starts the task and completes with the created run as soon as it exists (no waiting). {@code
   * input} optionally overrides the task's stored input ({@code null} to use the stored input).
   */
  public CompletableFuture<ActorRun> start(Object input, TaskStartOptions options) {
    return RunStartSupport.start(ctx, input, options::apply, options.contentTypeOrDefault());
  }

  /**
   * Starts the task and waits (non-blocking, polling) for it to finish. {@code waitSecs} bounds the
   * wait; {@code null} waits indefinitely.
   *
   * <p>This overload does not stream the run's log; use {@link #call(Object, TaskCallOptions,
   * Long)} for that (matching the reference client's default {@code call} behavior).
   */
  public CompletableFuture<ActorRun> call(Object input, TaskStartOptions options, Long waitSecs) {
    return RunStartSupport.call(
        root, ctx, input, options::apply, options.contentTypeOrDefault(), waitSecs);
  }

  /**
   * Starts the task and waits (non-blocking, polling) for it to finish, additionally streaming the
   * run's log for the duration of the wait — matching the reference client's {@code call}, whose
   * {@code options.log} defaults to {@code 'default'}. {@code waitSecs} bounds the wait; {@code
   * null} waits indefinitely.
   *
   * <p>Log streaming is best-effort: if starting it fails (e.g. the log is not yet available), the
   * run still starts and is still waited for, just without redirected log output. Use {@link
   * TaskCallOptions#disableLogStreaming()} to opt out entirely, or {@link
   * TaskCallOptions#logOptions(com.apify.client.log.StreamedLogOptions)} for a custom destination.
   */
  public CompletableFuture<ActorRun> call(Object input, TaskCallOptions options, Long waitSecs) {
    TaskCallOptions opts = options != null ? options : new TaskCallOptions();
    TaskStartOptions startOptions = opts.toStartOptions();
    return RunStartSupport.callWithLogStreaming(
        root,
        ctx,
        input,
        startOptions::apply,
        startOptions.contentTypeOrDefault(),
        opts.logStreamingEnabledValue(),
        opts.logOptionsValue(),
        waitSecs);
  }

  /** Fetches the task's stored input, or empty if none is set. */
  public CompletableFuture<Optional<JsonNode>> getInput() {
    return ctx.getRaw("input", new QueryParams())
        .thenApply(
            resp ->
                resp == null
                    ? Optional.empty()
                    : Optional.of(Json.parse(resp.body(), JsonNode.class)));
  }

  /** Replaces the task's stored input and returns the updated input. */
  public CompletableFuture<JsonNode> updateInput(Object input) {
    return ctx.putWithBodyNoEnvelope(
        "input",
        new QueryParams(),
        Json.toBytes(input),
        ResourceContext.CONTENT_TYPE_JSON,
        JsonNode.class);
  }

  /**
   * Returns a client for the last run of this task, optionally filtered by status (e.g. {@code
   * "SUCCEEDED"}). Pass {@code null} or empty for no filter.
   */
  public RunClient lastRun(String status) {
    return lastRun(new LastRunOptions().status(status));
  }

  /**
   * Returns a client for the last run of this task, optionally filtered by status and/or origin.
   */
  public RunClient lastRun(LastRunOptions options) {
    return RunClient.lastRun(root, http, ctx.subUrl(""), options);
  }

  /** A client for this task's run collection. */
  public RunCollectionClient runs() {
    return new RunCollectionClient(http, ctx.subUrl(""), "runs");
  }

  /**
   * A read-only client for this task's webhook collection ({@code GET
   * /v2/actor-tasks/{id}/webhooks}).
   */
  public NestedWebhookCollectionClient webhooks() {
    return new NestedWebhookCollectionClient(http, ctx.subUrl(""));
  }
}
