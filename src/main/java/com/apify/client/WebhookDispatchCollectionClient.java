package com.apify.client;

import java.util.Iterator;

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

  /**
   * Returns a lazy iterator over the webhook dispatches. The options' {@code limit} caps the total
   * number yielded ({@code null} = all); {@code chunkSize} is the per-request page size ({@code
   * null} = server default).
   */
  public Iterator<WebhookDispatch> iterate(ListOptions options) {
    return iterate(options, null);
  }

  /** As {@link #iterate(ListOptions)}, but {@code chunkSize} sets the per-request page size. */
  public Iterator<WebhookDispatch> iterate(ListOptions options, Long chunkSize) {
    ListOptions opts = options != null ? options : new ListOptions();
    return ctx.iterateResource(
        "",
        opts.limitValue(),
        chunkSize,
        opts.offsetValue(),
        opts::applyFilters,
        WebhookDispatch.class);
  }
}
