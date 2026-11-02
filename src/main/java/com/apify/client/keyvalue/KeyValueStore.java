package com.apify.client.keyvalue;

import com.apify.client.ApifyResource;
import com.apify.client.internal.NormalizedUrlDeserializer;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonDeserialize;

/** A key-value store holds arbitrary data records. */
public final class KeyValueStore extends ApifyResource {
  private String id;
  private String name;
  private String userId;
  private Instant createdAt;
  private Instant modifiedAt;
  private String title;

  @JsonDeserialize(using = NormalizedUrlDeserializer.class)
  private String consoleUrl;

  @JsonDeserialize(using = NormalizedUrlDeserializer.class)
  private String keysPublicUrl;

  @JsonDeserialize(using = NormalizedUrlDeserializer.class)
  private String recordsPublicUrl;

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

  /** The human-readable title of the store. */
  public String getTitle() {
    return title;
  }

  /** The URL of the store in the Apify Console (normalized). */
  public String getConsoleUrl() {
    return consoleUrl;
  }

  /**
   * A public URL of the store keys (signed when the store is not publicly accessible; normalized).
   */
  public String getKeysPublicUrl() {
    return keysPublicUrl;
  }

  /**
   * A public base URL of the store records (signed when the store is not publicly accessible;
   * normalized).
   */
  public String getRecordsPublicUrl() {
    return recordsPublicUrl;
  }
}
