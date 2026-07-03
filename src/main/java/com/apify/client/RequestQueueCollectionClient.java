package com.apify.client;

/** A client for the request queue collection ({@code GET/POST /v2/request-queues}). */
public final class RequestQueueCollectionClient {
  private final ResourceContext ctx;

  RequestQueueCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, "request-queues");
  }

  /** Lists request queues. */
  public PaginationList<RequestQueue> list(StorageListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, RequestQueue.class);
  }

  /**
   * Gets the queue with the given name, creating it if it does not exist. An empty/{@code null}
   * name creates a new unnamed queue.
   */
  public RequestQueue getOrCreate(String name) {
    return ctx.getOrCreateNamed(name, RequestQueue.class);
  }
}
