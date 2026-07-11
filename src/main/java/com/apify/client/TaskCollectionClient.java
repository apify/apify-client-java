package com.apify.client;

import java.util.Iterator;

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

  /**
   * Returns a lazy iterator over the account's tasks. The options' {@code limit} caps the total
   * number yielded ({@code null} = all); {@code chunkSize} is the per-request page size ({@code
   * null} = server default).
   */
  public Iterator<Task> iterate(ListOptions options) {
    return iterate(options, null);
  }

  /** As {@link #iterate(ListOptions)}, but {@code chunkSize} sets the per-request page size. */
  public Iterator<Task> iterate(ListOptions options, Long chunkSize) {
    ListOptions opts = options != null ? options : new ListOptions();
    return ctx.iterateResource(
        "", opts.limitValue(), chunkSize, opts.offsetValue(), opts::applyFilters, Task.class);
  }

  /** Creates a new task. {@code task} is any JSON-serializable task definition. */
  public Task create(Object task) {
    return ctx.createResource(new QueryParams(), task, Task.class);
  }
}
