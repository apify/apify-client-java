package com.apify.client.dataset;

import com.apify.client.ApifyResource;
import com.apify.client.internal.NormalizedUrlDeserializer;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonDeserialize;

/** A dataset stores structured results from Actor runs. */
public final class Dataset extends ApifyResource {
  private String id;
  private String name;
  private String userId;
  private Instant createdAt;
  private Instant modifiedAt;
  private long itemCount;
  private String title;

  @JsonDeserialize(using = NormalizedUrlDeserializer.class)
  private String consoleUrl;

  @JsonDeserialize(using = NormalizedUrlDeserializer.class)
  private String itemsPublicUrl;

  /** The unique dataset ID. */
  public String getId() {
    return id;
  }

  /** The dataset name (empty for unnamed datasets). */
  public String getName() {
    return name;
  }

  /** The ID of the user who owns the dataset. */
  public String getUserId() {
    return userId;
  }

  /** When the dataset was created. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** When the dataset was last modified. */
  public Instant getModifiedAt() {
    return modifiedAt;
  }

  /** The number of items currently stored. */
  public long getItemCount() {
    return itemCount;
  }

  /** The human-readable title of the dataset. */
  public String getTitle() {
    return title;
  }

  /** The URL of the dataset in the Apify Console (normalized). */
  public String getConsoleUrl() {
    return consoleUrl;
  }

  /**
   * A public URL of the dataset items (signed when the dataset is not publicly accessible;
   * normalized).
   */
  public String getItemsPublicUrl() {
    return itemsPublicUrl;
  }
}
