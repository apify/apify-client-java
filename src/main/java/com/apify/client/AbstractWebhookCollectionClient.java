package com.apify.client;

import java.util.Iterator;

/**
 * Shared read-only behavior for webhook collections. Both the account-wide collection ({@link
 * WebhookCollectionClient}) and the read-only collections nested under an Actor or task ({@link
 * NestedWebhookCollectionClient}) can list webhooks; only the account-wide collection can create
 * them. Internal base class.
 */
abstract class AbstractWebhookCollectionClient {
  final ResourceContext ctx;

  AbstractWebhookCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, "webhooks");
  }

  /** Lists webhooks. */
  public PaginationList<Webhook> list(ListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, Webhook.class);
  }

  /**
   * Returns a lazy iterator over the webhooks. The options' {@code limit} caps the total number
   * yielded ({@code null} = all); {@code chunkSize} is the per-request page size ({@code null} =
   * server default).
   */
  public Iterator<Webhook> iterate(ListOptions options) {
    return iterate(options, null);
  }

  /** As {@link #iterate(ListOptions)}, but {@code chunkSize} sets the per-request page size. */
  public Iterator<Webhook> iterate(ListOptions options, Long chunkSize) {
    ListOptions opts = options != null ? options : new ListOptions();
    return ctx.iterateResource(
        "", opts.limitValue(), chunkSize, opts.offsetValue(), opts::applyFilters, Webhook.class);
  }
}
