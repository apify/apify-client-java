package com.apify.client.store;

import com.apify.client.internal.AbstractCollectionClient;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.ResourceContext;

/** A client for browsing the Apify Store ({@code GET /v2/store}). */
public final class StoreCollectionClient
    extends AbstractCollectionClient<ActorStoreListItem, StoreListOptions> {

  public StoreCollectionClient(HttpClientCore http, String baseUrl) {
    super(
        ResourceContext.collection(http, baseUrl, ApiPaths.STORE),
        ActorStoreListItem.class,
        StoreListOptions::new);
  }
}
