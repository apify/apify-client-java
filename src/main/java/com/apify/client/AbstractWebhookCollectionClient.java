package com.apify.client;

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
}
