package com.apify.client.requestqueue;

import com.apify.client.ApifyResource;
import java.time.Instant;

/** A request queue stores URLs to be crawled. */
public final class RequestQueue extends ApifyResource {
  private String id;
  private String name;
  private String userId;
  private Instant createdAt;
  private Instant modifiedAt;
  private long totalRequestCount;

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
}
