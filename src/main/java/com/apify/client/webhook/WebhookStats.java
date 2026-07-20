package com.apify.client.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Usage statistics for a {@link Webhook}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WebhookStats {
  private long totalDispatches;

  /** The total number of times this webhook has been dispatched. */
  public long getTotalDispatches() {
    return totalDispatches;
  }
}
