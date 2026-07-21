package com.apify.client.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A summary of the {@link Webhook} that produced a {@link WebhookDispatch} (only the subset of
 * fields the API includes alongside a dispatch, not the full webhook object).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WebhookDispatchWebhookInfo {
  private String requestUrl;
  private boolean isAdHoc;

  /** The URL the webhook posts to. */
  public String getRequestUrl() {
    return requestUrl;
  }

  /**
   * Whether the webhook is a one-off webhook attached to a single run, rather than a persistent,
   * account-level webhook (see {@link Webhook#isAdHoc()}).
   */
  public boolean isAdHoc() {
    return isAdHoc;
  }
}
