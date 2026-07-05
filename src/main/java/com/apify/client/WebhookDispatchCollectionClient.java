package com.apify.client;

/**
 * A client for a webhook dispatch collection: the account-wide collection ({@code GET
 * /v2/webhook-dispatches}) or dispatches nested under a webhook.
 */
public final class WebhookDispatchCollectionClient {
  private final ResourceContext ctx;

  WebhookDispatchCollectionClient(HttpClientCore http, String baseUrl, String resourcePath) {
    this.ctx = ResourceContext.collection(http, baseUrl, resourcePath);
  }

  /** Lists webhook dispatches. */
  public PaginationList<WebhookDispatch> list(ListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, WebhookDispatch.class);
  }
}
