package com.apify.client.webhook;

import com.apify.client.ApifyResource;

/** A single invocation of a webhook. */
public final class WebhookDispatch extends ApifyResource {
  private String id;
  private String webhookId;

  /** The unique dispatch ID. */
  public String getId() {
    return id;
  }

  /** The ID of the webhook that produced this dispatch. */
  public String getWebhookId() {
    return webhookId;
  }
}
