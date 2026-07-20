package com.apify.client.build;

import com.apify.client.ListOptions;
import com.apify.client.internal.AbstractCollectionClient;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.ResourceContext;

/**
 * A client for a build collection: the account-wide collection ({@code GET /v2/actor-builds}) or an
 * Actor's builds ({@code GET /v2/actors/{id}/builds}).
 */
public final class BuildCollectionClient extends AbstractCollectionClient<Build, ListOptions> {

  public BuildCollectionClient(HttpClientCore http, String baseUrl, String resourcePath) {
    super(ResourceContext.collection(http, baseUrl, resourcePath), Build.class, ListOptions::new);
  }
}
