package com.apify.client.webhook;

import com.apify.client.ApifyResource;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** A webhook notifies an external service when specific events occur. */
public final class Webhook extends ApifyResource {
  private String id;
  private String userId;
  private Instant createdAt;
  private Instant modifiedAt;
  private boolean isAdHoc;
  private Boolean shouldInterpolateStrings;
  private List<String> eventTypes = List.of();
  private JsonNode condition;
  private boolean ignoreSslErrors;
  private boolean doNotRetry;
  private String requestUrl;
  private String payloadTemplate;
  private String headersTemplate;
  private String description;
  private WebhookLastDispatch lastDispatch;
  private WebhookStats stats;

  /** The unique webhook ID. */
  public String getId() {
    return id;
  }

  /** The ID of the user who owns the webhook. */
  public String getUserId() {
    return userId;
  }

  /** When the webhook was created. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** When the webhook was last modified. */
  public Instant getModifiedAt() {
    return modifiedAt;
  }

  /**
   * Whether this is a one-off webhook attached to a single run (e.g. via {@code
   * ActorStartOptions#webhooks}) rather than a persistent, account-level webhook.
   */
  public boolean isAdHoc() {
    return isAdHoc;
  }

  /**
   * Whether {@code {{...}}} placeholders in {@link #getPayloadTemplate()}/{@link
   * #getHeadersTemplate()} are interpolated with values from the triggering event before the
   * webhook is dispatched.
   */
  public Boolean getShouldInterpolateStrings() {
    return shouldInterpolateStrings;
  }

  /** The events that trigger the webhook (never {@code null}; unmodifiable). */
  public List<String> getEventTypes() {
    // Null-coalesce: Jackson binds directly to the (private) `eventTypes` field for
    // deserialization, which bypasses the `= List.of()` field initializer whenever the API
    // response contains an explicit `"eventTypes": null`.
    return eventTypes == null ? List.of() : Collections.unmodifiableList(eventTypes);
  }

  /**
   * The condition that must be met for the webhook to fire, as raw JSON (one of an Actor ID, a task
   * ID, or a specific run ID, depending on how the webhook was configured).
   */
  public JsonNode getCondition() {
    return condition;
  }

  /** Whether the webhook skips TLS certificate validation when calling {@link #getRequestUrl()}. */
  public boolean isIgnoreSslErrors() {
    return ignoreSslErrors;
  }

  /** Whether a failed dispatch is retried. */
  public boolean isDoNotRetry() {
    return doNotRetry;
  }

  /** The URL the webhook posts to. */
  public String getRequestUrl() {
    return requestUrl;
  }

  /** A template controlling the payload sent to {@link #getRequestUrl()}. */
  public String getPayloadTemplate() {
    return payloadTemplate;
  }

  /** A template controlling the extra HTTP headers sent with the dispatch, if any. */
  public String getHeadersTemplate() {
    return headersTemplate;
  }

  /** A description of what the webhook does. */
  public String getDescription() {
    return description;
  }

  /** A summary of this webhook's most recent dispatch, if it has ever fired. */
  public WebhookLastDispatch getLastDispatch() {
    return lastDispatch;
  }

  /** Usage statistics for this webhook. */
  public WebhookStats getStats() {
    return stats;
  }
}
