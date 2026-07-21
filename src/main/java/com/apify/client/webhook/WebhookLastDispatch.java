package com.apify.client.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** A summary of the most recent dispatch of a {@link Webhook}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WebhookLastDispatch {
  private String status;
  private Instant finishedAt;
  private Instant removedAt;

  /**
   * The dispatch's status: one of {@code "ACTIVE"} (still retrying), {@code "SUCCEEDED"}, or {@code
   * "FAILED"}.
   */
  public String getStatus() {
    return status;
  }

  /** When the dispatch reached a terminal state; {@code null} while still {@code "ACTIVE"}. */
  public Instant getFinishedAt() {
    return finishedAt;
  }

  /** When the dispatch record was removed, if applicable. */
  public Instant getRemovedAt() {
    return removedAt;
  }
}
