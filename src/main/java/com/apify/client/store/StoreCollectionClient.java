package com.apify.client.store;

import com.apify.client.PaginationList;
import com.apify.client.internal.AbstractCollectionClient;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.ListPublisher;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/** A client for browsing the Apify Store ({@code GET /v2/store}). */
public final class StoreCollectionClient
    extends AbstractCollectionClient<ActorStoreListItem, StoreListOptions> {

  public StoreCollectionClient(HttpClientCore http, String baseUrl) {
    super(
        ResourceContext.collection(http, baseUrl, ApiPaths.STORE),
        ActorStoreListItem.class,
        StoreListOptions::new);
  }

  /**
   * As {@link AbstractCollectionClient#iterate(com.apify.client.internal.ListOptionsLike, Long)},
   * except that a search returns a single page: the API ignores {@code offset} and has no second
   * page, so paging would read the same page forever. The request is then sent once, when {@code
   * iterate} is called, with the options' {@code limit} (when positive) as its page size and cap;
   * {@code chunkSize} and {@code offset} have no effect.
   */
  @Override
  public Flow.Publisher<ActorStoreListItem> iterate(StoreListOptions options, Long chunkSize) {
    StoreListOptions opts = options != null ? options : defaultOptions();
    if (!opts.hasSearch()) {
      return super.iterate(opts, chunkSize);
    }
    Long limit = opts.limitValue();
    QueryParams params = new QueryParams();
    opts.applyFilters(params);
    if (limit != null && limit > 0) {
      params.addLong("limit", limit);
    }
    CompletableFuture<List<ActorStoreListItem>> items =
        listWithParams(params).thenApply(PaginationList::getItems);
    return new ListPublisher<>(items);
  }
}
