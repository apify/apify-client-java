package com.apify.client;

import java.util.Optional;

/** A client for a specific webhook dispatch ({@code /v2/webhook-dispatches/{dispatchId}}). */
public final class WebhookDispatchClient {
  private final ResourceContext ctx;

  WebhookDispatchClient(HttpClientCore http, String baseUrl, String id) {
    this.ctx = ResourceContext.single(http, baseUrl, "webhook-dispatches", id);
  }

  /** Fetches the dispatch, or empty if it does not exist. */
  public Optional<WebhookDispatch> get() {
    return ctx.getResource("", new QueryParams(), WebhookDispatch.class);
  }
}
