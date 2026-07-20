package com.apify.client.build;

import com.apify.client.ListOptions;
import com.apify.client.PaginationList;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.Iterator;

/**
 * A client for a build collection: the account-wide collection ({@code GET /v2/actor-builds}) or an
 * Actor's builds ({@code GET /v2/actors/{id}/builds}).
 */
public final class BuildCollectionClient {
  private final ResourceContext ctx;

  public BuildCollectionClient(HttpClientCore http, String baseUrl, String resourcePath) {
    this.ctx = ResourceContext.collection(http, baseUrl, resourcePath);
  }

  /** Lists builds. */
  public PaginationList<Build> list(ListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, Build.class);
  }

  /**
   * Returns a lazy iterator over the builds. The options' {@code limit} caps the total number
   * yielded ({@code null} or non-positive = all); {@code chunkSize} is the per-request page size
   * ({@code null} = server default).
   */
  public Iterator<Build> iterate(ListOptions options) {
    return iterate(options, null);
  }

  /** As {@link #iterate(ListOptions)}, but {@code chunkSize} sets the per-request page size. */
  public Iterator<Build> iterate(ListOptions options, Long chunkSize) {
    ListOptions opts = options != null ? options : new ListOptions();
    return ctx.iterateResource(
        "", opts.limitValue(), chunkSize, opts.offsetValue(), opts::applyFilters, Build.class);
  }
}
