package com.apify.client.build;

import com.apify.client.ApifyResource;
import com.apify.client.internal.Statuses;
import java.time.Instant;

/** A single build of an Actor. */
public final class Build extends ApifyResource {
  private String id;
  private String actId;
  private String status;
  private Instant startedAt;
  private Instant finishedAt;
  private String buildNumber;

  /** The unique build ID. */
  public String getId() {
    return id;
  }

  /** The ID of the Actor this build belongs to. */
  public String getActId() {
    return actId;
  }

  /**
   * The current build status. One of the eight {@code ActorJobStatus} values: {@code READY}, {@code
   * RUNNING}, {@code SUCCEEDED}, {@code FAILED}, {@code TIMING-OUT}, {@code TIMED-OUT}, {@code
   * ABORTING}, {@code ABORTED}.
   */
  public String getStatus() {
    return status;
  }

  /** When the build started. */
  public Instant getStartedAt() {
    return startedAt;
  }

  /** When the build finished (absent while still building). */
  public Instant getFinishedAt() {
    return finishedAt;
  }

  /** The human-readable build number (e.g. {@code "0.1.2"}). */
  public String getBuildNumber() {
    return buildNumber;
  }

  /** Whether the build has reached a terminal (finished) status. */
  public boolean isTerminal() {
    return Statuses.isTerminal(status);
  }
}
