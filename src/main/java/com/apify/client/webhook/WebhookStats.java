package com.apify.client.webhook;

import com.apify.client.ApifyResource;

/** Usage statistics for a {@link Webhook}. */
public final class WebhookStats extends ApifyResource {
  private long totalDispatches;

  /** The total number of times this webhook has been dispatched. */
  public long getTotalDispatches() {
    return totalDispatches;
  }
}
