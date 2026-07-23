package com.apify.client.webhook;

import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.Optional;

/** A client for a specific webhook ({@code /v2/webhooks/{webhookId}}). */
public final class WebhookClient {
  private final HttpClientCore http;
  private final ResourceContext ctx;

  public WebhookClient(HttpClientCore http, String baseUrl, String id) {
    this.http = http;
    this.ctx = ResourceContext.single(http, baseUrl, ApiPaths.WEBHOOKS, id);
  }

  /** Fetches the webhook, or empty if it does not exist. */
  public Optional<Webhook> get() {
    return ctx.getResource("", new QueryParams(), Webhook.class);
  }

  /** Updates the webhook with the given fields and returns the updated object. */
  public Webhook update(Object newFields) {
    return ctx.updateResource("", newFields, Webhook.class);
  }

  /** Deletes the webhook. */
  public void delete() {
    ctx.deleteResource("");
  }

  /** Dispatches the webhook immediately and returns the resulting dispatch. */
  public WebhookDispatch test() {
    return ctx.postWithBody("test", new QueryParams(), null, "", WebhookDispatch.class);
  }

  /** A client for this webhook's dispatch collection. */
  public WebhookDispatchCollectionClient dispatches() {
    return new WebhookDispatchCollectionClient(http, ctx.subUrl(""), "dispatches");
  }
}
