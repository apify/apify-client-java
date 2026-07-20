package com.apify.client.requestqueue;

import com.apify.client.PaginationList;
import com.apify.client.StorageListOptions;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.Iterator;

/** A client for the request queue collection ({@code GET/POST /v2/request-queues}). */
public final class RequestQueueCollectionClient {
  private final ResourceContext ctx;

  public RequestQueueCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, ApiPaths.REQUEST_QUEUES);
  }

  /** Lists request queues. */
  public PaginationList<RequestQueue> list(StorageListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, RequestQueue.class);
  }

  /**
   * Returns a lazy iterator over the request queues. The options' {@code limit} caps the total
   * number yielded ({@code null} or non-positive = all); {@code chunkSize} is the per-request page
   * size ({@code null} = server default).
   */
  public Iterator<RequestQueue> iterate(StorageListOptions options) {
    return iterate(options, null);
  }

  /**
   * As {@link #iterate(StorageListOptions)}, but {@code chunkSize} sets the per-request page size.
   */
  public Iterator<RequestQueue> iterate(StorageListOptions options, Long chunkSize) {
    StorageListOptions opts = options != null ? options : new StorageListOptions();
    return ctx.iterateResource(
        "",
        opts.limitValue(),
        chunkSize,
        opts.offsetValue(),
        opts::applyFilters,
        RequestQueue.class);
  }

  /**
   * Gets the queue with the given name, creating it if it does not exist. An empty/{@code null}
   * name creates a new unnamed queue.
   */
  public RequestQueue getOrCreate(String name) {
    return ctx.getOrCreateNamed(name, RequestQueue.class);
  }
}
