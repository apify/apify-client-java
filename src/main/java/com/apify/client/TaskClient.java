package com.apify.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

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

  TaskClient(ApifyClient root, HttpClientCore http, String baseUrl, String id) {
    this.root = root;
    this.http = http;
    this.ctx = ResourceContext.single(http, baseUrl, "actor-tasks", id);
  }

  /** Fetches the task object, or empty if it does not exist. */
  public Optional<Task> get() {
    return ctx.getResource("", new QueryParams(), Task.class);
  }

  /** Updates the task with the given fields and returns the updated object. */
  public Task update(Object newFields) {
    return ctx.updateResource("", newFields, Task.class);
  }

  /** Deletes the task. */
  public void delete() {
    ctx.deleteResource("");
  }

  /**
   * Starts the task and returns immediately with the created run. {@code input} optionally
   * overrides the task's stored input ({@code null} to use the stored input).
   */
  public ActorRun start(Object input, TaskStartOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    byte[] body = input == null ? null : Json.toBytes(input);
    return ctx.postWithBody(
        "runs", params, body, ResourceContext.CONTENT_TYPE_JSON, ActorRun.class);
  }

  /**
   * Starts the task and waits (client-side polling) for it to finish. {@code waitSecs} bounds the
   * wait; {@code null} waits indefinitely.
   */
  public ActorRun call(Object input, TaskStartOptions options, Long waitSecs) {
    ActorRun run = start(input, options);
    return root.run(run.getId()).waitForFinish(waitSecs);
  }

  /** Fetches the task's stored input, or empty if none is set. */
  public Optional<JsonNode> getInput() {
    ApiResponse resp = ctx.getRaw("input", new QueryParams());
    if (resp == null) {
      return Optional.empty();
    }
    return Optional.of(Json.parse(resp.body, JsonNode.class));
  }

  /** Replaces the task's stored input and returns the updated input. */
  public JsonNode updateInput(Object input) {
    ApiResponse resp =
        http.call(
            "PUT",
            ctx.subUrl("input"),
            Json.toBytes(input),
            ResourceContext.CONTENT_TYPE_JSON,
            ResourceContext.DEFAULT_REQUEST_TIMEOUT);
    return Json.parse(resp.body, JsonNode.class);
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
    RunClient client = new RunClient(root, http, ctx.subUrl(""), "runs", "last");
    client.setLastRunParams(options);
    return client;
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
