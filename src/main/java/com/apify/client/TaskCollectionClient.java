package com.apify.client;

/** A client for the Actor task collection ({@code GET/POST /v2/actor-tasks}). */
public final class TaskCollectionClient {
  private final ResourceContext ctx;

  TaskCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, "actor-tasks");
  }

  /** Lists the account's tasks. */
  public PaginationList<Task> list(ListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, Task.class);
  }

  /** Creates a new task. {@code task} is any JSON-serializable task definition. */
  public Task create(Object task) {
    return ctx.createResource(new QueryParams(), task, Task.class);
  }
}
