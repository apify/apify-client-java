package com.apify.client.task;

import com.apify.client.ApifyResource;
import com.apify.client.actor.ActorStandby;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** A pre-configured Actor run (an Actor task). */
public final class Task extends ApifyResource {
  private String id;
  private String actId;
  private String userId;
  private String name;
  private String title;
  private String description;
  private Instant createdAt;
  private Instant modifiedAt;
  private TaskStats stats;
  private TaskOptions options;
  private JsonNode input;
  private ActorStandby actorStandby;

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

  /** A description of what the task does. */
  public String getDescription() {
    return description;
  }

  /** When the task was created. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** When the task was last modified. */
  public Instant getModifiedAt() {
    return modifiedAt;
  }

  /** Usage statistics for this task. */
  public TaskStats getStats() {
    return stats;
  }

  /** The task's stored default run configuration. */
  public TaskOptions getOptions() {
    return options;
  }

  /**
   * The task's stored input, as raw JSON (an object or array of objects, depending on the Actor's
   * input schema); {@code null} if none is set. Use {@link TaskClient#getInput()} to fetch it
   * on-demand instead of relying on this snapshot.
   */
  public JsonNode getInput() {
    return input;
  }

  /** Standby-mode configuration overrides for this task, if any. */
  public ActorStandby getActorStandby() {
    return actorStandby;
  }
}
