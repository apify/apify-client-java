package com.apify.client.webhook;

import com.apify.client.ApiPaths;
import com.apify.client.QueryParams;
import com.apify.client.ResourceContext;
import com.apify.client.http.HttpClientCore;
import java.util.Optional;

/** A client for a specific webhook dispatch ({@code /v2/webhook-dispatches/{dispatchId}}). */
public final class WebhookDispatchClient {
  private final ResourceContext ctx;

  public WebhookDispatchClient(HttpClientCore http, String baseUrl, String id) {
    this.ctx = ResourceContext.single(http, baseUrl, ApiPaths.WEBHOOK_DISPATCHES, id);
  }

  /** Fetches the dispatch, or empty if it does not exist. */
  public Optional<WebhookDispatch> get() {
    return ctx.getResource("", new QueryParams(), WebhookDispatch.class);
  }
}
