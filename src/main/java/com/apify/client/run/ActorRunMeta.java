package com.apify.client.run;

import com.apify.client.ApifyResource;
import java.time.Instant;

/** Metadata about how an {@link ActorRun} was initiated. */
public final class ActorRunMeta extends ApifyResource {
  private String origin;
  private String clientIp;
  private String userAgent;
  private String scheduleId;
  private Instant scheduledAt;

  /** What triggered the run (e.g. {@code "WEB"}, {@code "API"}, {@code "SCHEDULER"}). */
  public String getOrigin() {
    return origin;
  }

  /** The IP address of the client that started the run, if known. */
  public String getClientIp() {
    return clientIp;
  }

  /** The {@code User-Agent} of the client that started the run. */
  public String getUserAgent() {
    return userAgent;
  }

  /** The ID of the schedule that triggered the run, if any. */
  public String getScheduleId() {
    return scheduleId;
  }

  /** When the run was scheduled, if it was triggered by a schedule. */
  public Instant getScheduledAt() {
    return scheduledAt;
  }
}
