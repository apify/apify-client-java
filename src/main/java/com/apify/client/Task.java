package com.apify.client;

import java.time.Instant;

/** A pre-configured Actor run (an Actor task). */
public final class Task extends ApifyResource {
  private String id;
  private String actId;
  private String userId;
  private String name;
  private String title;
  private Instant createdAt;
  private Instant modifiedAt;

  /** The unique task ID. */
  public String getId() {
    return id;
  }

  /** The ID of the Actor this task runs. */
  public String getActId() {
    return actId;
  }

  /** The ID of the user who owns the task. */
  public String getUserId() {
    return userId;
  }

  /** The technical name of the task. */
  public String getName() {
    return name;
  }

  /** The human-readable title shown in the UI. */
  public String getTitle() {
    return title;
  }

  /** When the task was created. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** When the task was last modified. */
  public Instant getModifiedAt() {
    return modifiedAt;
  }
}
