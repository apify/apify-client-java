package com.apify.client;

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
}
