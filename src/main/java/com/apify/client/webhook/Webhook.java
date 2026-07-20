package com.apify.client.webhook;

import com.apify.client.ApifyResource;
import java.util.Collections;
import java.util.List;

/** A webhook notifies an external service when specific events occur. */
public final class Webhook extends ApifyResource {
  private String id;
  private String userId;
  private String requestUrl;
  private List<String> eventTypes = List.of();

  /** The unique webhook ID. */
  public String getId() {
    return id;
  }

  /** The ID of the user who owns the webhook. */
  public String getUserId() {
    return userId;
  }

  /** The URL the webhook posts to. */
  public String getRequestUrl() {
    return requestUrl;
  }

  /** The events that trigger the webhook. */
  public List<String> getEventTypes() {
    return Collections.unmodifiableList(eventTypes);
  }
}
