package com.apify.client;

import java.util.Iterator;

/**
 * A client for a run collection: the account-wide collection ({@code GET /v2/actor-runs}), an
 * Actor's runs ({@code GET /v2/actors/{id}/runs}), or a task's runs ({@code GET
 * /v2/actor-tasks/{id}/runs}).
 */
public final class RunCollectionClient {
  private final ResourceContext ctx;

  RunCollectionClient(HttpClientCore http, String baseUrl, String resourcePath) {
    this.ctx = ResourceContext.collection(http, baseUrl, resourcePath);
  }

  /**
   * Lists runs, applying the standard pagination and the run-specific filters. Both {@code options}
   * and {@code filter} may be {@code null}, which is treated as "no options"/"no filter".
   */
  public PaginationList<ActorRun> list(ListOptions options, RunListOptions filter) {
    QueryParams params = new QueryParams();
    if (options != null) {
      options.apply(params);
    }
    if (filter != null) {
      filter.apply(params);
    }
    return ctx.listResource("", params, ActorRun.class);
  }

  /**
   * Returns a lazy iterator over the runs, applying the standard pagination and run-specific
   * filters. The options' {@code limit} caps the total number yielded ({@code null} or non-positive
   * = all); {@code chunkSize} is the per-request page size ({@code null} = server default). Both
   * {@code options} and {@code filter} may be {@code null}.
   */
  public Iterator<ActorRun> iterate(ListOptions options, RunListOptions filter) {
    return iterate(options, filter, null);
  }

  /**
   * As {@link #iterate(ListOptions, RunListOptions)}, but {@code chunkSize} sets the per-request
   * page size.
   */
  public Iterator<ActorRun> iterate(ListOptions options, RunListOptions filter, Long chunkSize) {
    ListOptions opts = options != null ? options : new ListOptions();
    return ctx.iterateResource(
        "",
        opts.limitValue(),
        chunkSize,
        opts.offsetValue(),
        p -> {
          opts.applyFilters(p);
          if (filter != null) {
            filter.apply(p);
          }
        },
        ActorRun.class);
  }
}
