package com.apify.client.schedule;

import com.apify.client.ApifyResource;
import java.time.Instant;

/**
 * A single entry of a schedule's invocation log, as returned by {@link ScheduleClient#getLog()}.
 */
public final class ScheduleInvoked extends ApifyResource {
  private String message;
  private String level;
  private Instant createdAt;

  /** The human-readable log message. */
  public String getMessage() {
    return message;
  }

  /** The log level (e.g. {@code "INFO"}, {@code "ERROR"}). */
  public String getLevel() {
    return level;
  }

  /** When this entry was recorded. */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
