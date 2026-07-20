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
  private List<String> eventTypes = List.of();
  private JsonNode condition;
  private boolean ignoreSslErrors;
  private boolean doNotRetry;
  private String requestUrl;
  private String payloadTemplate;
  private String headersTemplate;
  private String description;
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

  /** The events that trigger the webhook. */
  public List<String> getEventTypes() {
    return Collections.unmodifiableList(eventTypes);
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

  /** Usage statistics for this webhook. */
  public WebhookStats getStats() {
    return stats;
  }
}
