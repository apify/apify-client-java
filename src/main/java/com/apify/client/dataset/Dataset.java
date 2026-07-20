package com.apify.client.dataset;

import com.apify.client.ApifyResource;
import java.time.Instant;

/** A dataset stores structured results from Actor runs. */
public final class Dataset extends ApifyResource {
  private String id;
  private String name;
  private String userId;
  private Instant createdAt;
  private Instant modifiedAt;
  private long itemCount;

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
}
