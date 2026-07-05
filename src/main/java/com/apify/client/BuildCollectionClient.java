package com.apify.client;

/**
 * A client for a build collection: the account-wide collection ({@code GET /v2/actor-builds}) or an
 * Actor's builds ({@code GET /v2/actors/{id}/builds}).
 */
public final class BuildCollectionClient {
  private final ResourceContext ctx;

  BuildCollectionClient(HttpClientCore http, String baseUrl, String resourcePath) {
    this.ctx = ResourceContext.collection(http, baseUrl, resourcePath);
  }

  /** Lists builds. */
  public PaginationList<Build> list(ListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, Build.class);
  }
}
