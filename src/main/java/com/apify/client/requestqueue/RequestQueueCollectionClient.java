package com.apify.client.requestqueue;

import com.apify.client.StorageListOptions;
import com.apify.client.internal.AbstractCollectionClient;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.ResourceContext;

/** A client for the request queue collection ({@code GET/POST /v2/request-queues}). */
public final class RequestQueueCollectionClient
    extends AbstractCollectionClient<RequestQueue, StorageListOptions> {

  public RequestQueueCollectionClient(HttpClientCore http, String baseUrl) {
    super(
        ResourceContext.collection(http, baseUrl, ApiPaths.REQUEST_QUEUES),
        RequestQueue.class,
        StorageListOptions::new);
  }

  /**
   * Gets the queue with the given name, creating it if it does not exist. An empty/{@code null}
   * name creates a new unnamed queue.
   */
  public RequestQueue getOrCreate(String name) {
    return ctx.getOrCreateNamed(name, RequestQueue.class);
  }
}
