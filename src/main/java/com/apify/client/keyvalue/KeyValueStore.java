package com.apify.client.keyvalue;

import com.apify.client.ApifyResource;
import java.time.Instant;

/** A key-value store holds arbitrary data records. */
public final class KeyValueStore extends ApifyResource {
  private String id;
  private String name;
  private String userId;
  private Instant createdAt;
  private Instant modifiedAt;

  /** The unique store ID. */
  public String getId() {
    return id;
  }

  /** The store name (empty for unnamed stores). */
  public String getName() {
    return name;
  }

  /** The ID of the user who owns the store. */
  public String getUserId() {
    return userId;
  }

  /** When the store was created. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** When the store was last modified. */
  public Instant getModifiedAt() {
    return modifiedAt;
  }
}
