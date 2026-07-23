package com.apify.client.webhook;

import com.apify.client.ListOptions;
import com.apify.client.PaginationList;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Shared read-only behavior for webhook collections. Both the account-wide collection ({@link
 * WebhookCollectionClient}) and the read-only collections nested under an Actor or task ({@link
 * NestedWebhookCollectionClient}) can list webhooks; only the account-wide collection can create
 * them. Internal base class.
 */
abstract class AbstractWebhookCollectionClient {
  final ResourceContext ctx;

  AbstractWebhookCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, ApiPaths.WEBHOOKS);
  }

  /** Lists webhooks. */
  public CompletableFuture<PaginationList<Webhook>> list(ListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, Webhook.class);
  }

  /**
   * Returns a lazy, backpressure-aware publisher over the webhooks. The options' {@code limit} caps
   * the total number yielded ({@code null} or non-positive = all); {@code chunkSize} is the
   * per-request page size ({@code null} = server default).
   */
  public Flow.Publisher<Webhook> iterate(ListOptions options) {
    return iterate(options, null);
  }

  /** As {@link #iterate(ListOptions)}, but {@code chunkSize} sets the per-request page size. */
  public Flow.Publisher<Webhook> iterate(ListOptions options, Long chunkSize) {
    ListOptions opts = options != null ? options : new ListOptions();
    return ctx.iterateResource(
        "", opts.limitValue(), chunkSize, opts.offsetValue(), opts::applyFilters, Webhook.class);
  }
}
