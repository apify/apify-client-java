package com.apify.client;

/** A schedule automatically starts Actor or task runs at specified times. */
public final class Schedule extends ApifyResource {
  private String id;
  private String userId;
  private String name;
  private String cronExpression;
  private boolean isEnabled;

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

  /** The cron expression governing when the schedule fires. */
  public String getCronExpression() {
    return cronExpression;
  }

  /** Whether the schedule is currently active. */
  public boolean isEnabled() {
    return isEnabled;
  }
}
