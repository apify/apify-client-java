package com.apify.client;

import java.util.Iterator;

/** A client for browsing the Apify Store ({@code GET /v2/store}). */
public final class StoreCollectionClient {
  private final ResourceContext ctx;

  StoreCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, "store");
  }

  /** Returns a single page of Store Actors matching the options. */
  public PaginationList<ActorStoreListItem> list(StoreListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, ActorStoreListItem.class);
  }

  /**
   * Returns a lazy iterator over Store Actors matching the options, fetching pages on demand. The
   * options' {@code limit} caps the total number of Actors yielded ({@code null} = all); {@code
   * chunkSize} is the per-request page size ({@code null} = server default).
   */
  public Iterator<ActorStoreListItem> iterate(StoreListOptions options) {
    return iterate(options, null);
  }

  /**
   * As {@link #iterate(StoreListOptions)}, but {@code chunkSize} sets the per-request page size.
   */
  public Iterator<ActorStoreListItem> iterate(StoreListOptions options, Long chunkSize) {
    StoreListOptions opts = options != null ? options : new StoreListOptions();
    return ctx.iterateResource(
        "",
        opts.limitValue(),
        chunkSize,
        opts.offsetValue(),
        opts::applyFilters,
        ActorStoreListItem.class);
  }
}
