package com.apify.client.schedule;

import com.apify.client.ApifyResource;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** A schedule automatically starts Actor or task runs at specified times. */
public final class Schedule extends ApifyResource {
  private String id;
  private String userId;
  private String name;
  private String title;
  private String description;
  private String cronExpression;
  private String timezone;
  private boolean isEnabled;
  private boolean isExclusive;
  private Instant createdAt;
  private Instant modifiedAt;
  private Instant nextRunAt;
  private Instant lastRunAt;
  private List<JsonNode> actions = List.of();
  private ScheduleNotifications notifications;

  /** The unique schedule ID. */
  public String getId() {
    return id;
  }

  /** The ID of the user who owns the schedule. */
  public String getUserId() {
    return userId;
  }

  /** The schedule name. */
  public String getName() {
    return name;
  }

  /** The human-readable title shown in the UI. */
  public String getTitle() {
    return title;
  }

  /** A description of what the schedule does. */
  public String getDescription() {
    return description;
  }

  /** The cron expression governing when the schedule fires. */
  public String getCronExpression() {
    return cronExpression;
  }

  /** The IANA timezone name (e.g. {@code "UTC"}, {@code "America/New_York"}) the cron runs in. */
  public String getTimezone() {
    return timezone;
  }

  /** Whether the schedule is currently active. */
  public boolean isEnabled() {
    return isEnabled;
  }

  /**
   * Whether the schedule is exclusive: a new run is skipped while a previous one is still active.
   */
  public boolean isExclusive() {
    return isExclusive;
  }

  /** When the schedule was created. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** When the schedule was last modified. */
  public Instant getModifiedAt() {
    return modifiedAt;
  }

  /** When the schedule is next due to fire, or {@code null} if it is disabled. */
  public Instant getNextRunAt() {
    return nextRunAt;
  }

  /** When the schedule last fired, or {@code null} if it never has. */
  public Instant getLastRunAt() {
    return lastRunAt;
  }

  /**
   * The Actor/task-run actions this schedule triggers, as raw JSON (each action's shape depends on
   * its {@code type}, {@code RUN_ACTOR} or {@code RUN_ACTOR_TASK}; never {@code null};
   * unmodifiable).
   */
  public List<JsonNode> getActions() {
    // Null-coalesce: Jackson binds directly to the (private) `actions` field for deserialization,
    // which bypasses the `= List.of()` field initializer whenever the API response contains an
    // explicit `"actions": null`.
    return actions == null ? List.of() : Collections.unmodifiableList(actions);
  }

  /** Notification settings for this schedule. */
  public ScheduleNotifications getNotifications() {
    return notifications;
  }
}
