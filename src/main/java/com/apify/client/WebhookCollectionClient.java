package com.apify.client;

/**
 * A client for the account-wide webhook collection ({@code GET/POST /v2/webhooks}), supporting both
 * listing and creation. Webhooks nested under an Actor or task are read-only and use {@link
 * NestedWebhookCollectionClient} instead.
 */
public final class WebhookCollectionClient extends AbstractWebhookCollectionClient {
  WebhookCollectionClient(HttpClientCore http, String baseUrl) {
    super(http, baseUrl);
  }

  /** Creates a new webhook. {@code webhook} is any JSON-serializable webhook definition. */
  public Webhook create(Object webhook) {
    return ctx.createResource(new QueryParams(), webhook, Webhook.class);
  }
}
