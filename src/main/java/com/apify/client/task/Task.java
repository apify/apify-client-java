package com.apify.client.task;

import com.apify.client.ApifyResource;
import com.apify.client.actor.ActorStandby;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

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
  private Boolean isPublic;
  private TaskPublicConfig publicConfig;

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

  /**
   * Whether the task is published on its public landing page. Not part of the documented {@code
   * Task} schema in the OpenAPI spec, but the API returns it in practice (mirroring the reference
   * JS client). Use {@link TaskClient#publish()} / {@link TaskClient#unpublish()} to change it.
   */
  public Boolean getIsPublic() {
    return isPublic;
  }

  /** The task's public landing page display configuration, if it has one. */
  public TaskPublicConfig getPublicConfig() {
    return publicConfig;
  }
}
