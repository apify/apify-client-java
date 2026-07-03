package com.apify.client;

import java.time.Instant;

/** An Actor on the Apify platform. */
public final class Actor extends ApifyResource {
  private String id;
  private String userId;
  private String name;
  private String username;
  private String title;
  private String description;
  private boolean isPublic;
  private Instant createdAt;
  private Instant modifiedAt;

  /** The unique Actor ID. */
  public String getId() {
    return id;
  }

  /** The ID of the user who owns the Actor. */
  public String getUserId() {
    return userId;
  }

  /** The technical name of the Actor (used in API paths). */
  public String getName() {
    return name;
  }

  /** The username of the Actor's owner. */
  public String getUsername() {
    return username;
  }

  /** The human-readable title shown in the UI. */
  public String getTitle() {
    return title;
  }

  /** A description of what the Actor does. */
  public String getDescription() {
    return description;
  }

  /** Whether the Actor is publicly available in Apify Store. */
  public boolean isPublic() {
    return isPublic;
  }

  /** When the Actor was created. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** When the Actor was last modified. */
  public Instant getModifiedAt() {
    return modifiedAt;
  }
}
