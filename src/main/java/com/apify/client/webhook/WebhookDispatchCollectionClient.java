package com.apify.client.webhook;

import com.apify.client.ListOptions;
import com.apify.client.internal.AbstractCollectionClient;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.ResourceContext;

/**
 * A client for a webhook dispatch collection: the account-wide collection ({@code GET
 * /v2/webhook-dispatches}) or dispatches nested under a webhook.
 */
public final class WebhookDispatchCollectionClient
    extends AbstractCollectionClient<WebhookDispatch, ListOptions> {

  public WebhookDispatchCollectionClient(HttpClientCore http, String baseUrl, String resourcePath) {
    super(
        ResourceContext.collection(http, baseUrl, resourcePath),
        WebhookDispatch.class,
        ListOptions::new);
  }
}
