package com.apify.client.requestqueue;

import com.apify.client.ApifyResource;
import com.apify.client.internal.NormalizedUrlDeserializer;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonDeserialize;

/** A request queue stores URLs to be crawled. */
public final class RequestQueue extends ApifyResource {
  private String id;
  private String name;
  private String userId;
  private Instant createdAt;
  private Instant modifiedAt;
  private long totalRequestCount;
  private String title;

  @JsonDeserialize(using = NormalizedUrlDeserializer.class)
  private String consoleUrl;

  /** The unique queue ID. */
  public String getId() {
    return id;
  }

  /** The queue name (empty for unnamed queues). */
  public String getName() {
    return name;
  }

  /** The ID of the user who owns the queue. */
  public String getUserId() {
    return userId;
  }

  /** When the queue was created. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** When the queue was last modified. */
  public Instant getModifiedAt() {
    return modifiedAt;
  }

  /** The total number of requests ever added. */
  public long getTotalRequestCount() {
    return totalRequestCount;
  }

  /** The human-readable title of the queue. */
  public String getTitle() {
    return title;
  }

  /** The URL of the queue in the Apify Console (normalized). */
  public String getConsoleUrl() {
    return consoleUrl;
  }
}
