package com.apify.client.webhook;

import com.apify.client.ApifyResource;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** A single invocation of a webhook. */
public final class WebhookDispatch extends ApifyResource {
  private String id;
  private String userId;
  private String webhookId;
  private Instant createdAt;
  private String status;
  private String eventType;
  private List<WebhookDispatchCall> calls = List.of();
  private WebhookDispatchWebhookInfo webhook;
  private WebhookDispatchEventData eventData;

  /** The unique dispatch ID. */
  public String getId() {
    return id;
  }

  /** The ID of the user who owns the webhook that produced this dispatch. */
  public String getUserId() {
    return userId;
  }

  /** The ID of the webhook that produced this dispatch. */
  public String getWebhookId() {
    return webhookId;
  }

  /** When the dispatch was created. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * The dispatch's status: one of {@code "ACTIVE"} (still retrying), {@code "SUCCEEDED"}, or {@code
   * "FAILED"}.
   */
  public String getStatus() {
    return status;
  }

  /** The type of event that triggered this dispatch (see {@link Webhook#getEventTypes()}). */
  public String getEventType() {
    return eventType;
  }

  /**
   * The individual HTTP delivery attempts made for this dispatch (unmodifiable, never {@code
   * null}).
   */
  public List<WebhookDispatchCall> getCalls() {
    // Null-coalesce: Jackson binds directly to the (private) `calls` field for deserialization,
    // which bypasses the `= List.of()` field initializer whenever the API response contains an
    // explicit `"calls": null`.
    return calls == null ? List.of() : Collections.unmodifiableList(calls);
  }

  /** A summary of the webhook that produced this dispatch, if included by the API. */
  public WebhookDispatchWebhookInfo getWebhook() {
    return webhook;
  }

  /** The event payload that triggered this dispatch, if any. */
  public WebhookDispatchEventData getEventData() {
    return eventData;
  }
}
