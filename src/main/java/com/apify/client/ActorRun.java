package com.apify.client;

import java.time.Instant;

/** A single execution of an Actor. */
public final class ActorRun extends ApifyResource {
  private String id;
  private String actId;
  private String actorTaskId;
  private String userId;
  private String status;
  private String statusMessage;
  private Instant startedAt;
  private Instant finishedAt;
  private String buildId;
  private String defaultDatasetId;
  private String defaultKeyValueStoreId;
  private String defaultRequestQueueId;
  private String containerUrl;

  /** The unique run ID. */
  public String getId() {
    return id;
  }

  /** The ID of the Actor that produced this run. */
  public String getActId() {
    return actId;
  }

  /** The ID of the task that started this run, if any. */
  public String getActorTaskId() {
    return actorTaskId;
  }

  /** The ID of the user who owns the run. */
  public String getUserId() {
    return userId;
  }

  /**
   * The current run status. One of the eight {@code ActorJobStatus} values: {@code READY}, {@code
   * RUNNING}, {@code SUCCEEDED}, {@code FAILED}, {@code TIMING-OUT}, {@code TIMED-OUT}, {@code
   * ABORTING}, {@code ABORTED}.
   */
  public String getStatus() {
    return status;
  }

  /** An optional human-readable status message. */
  public String getStatusMessage() {
    return statusMessage;
  }

  /** When the run started. */
  public Instant getStartedAt() {
    return startedAt;
  }

  /** When the run finished (absent while still running). */
  public Instant getFinishedAt() {
    return finishedAt;
  }

  /** The ID of the build used for the run. */
  public String getBuildId() {
    return buildId;
  }

  /** The ID of the run's default dataset. */
  public String getDefaultDatasetId() {
    return defaultDatasetId;
  }

  /** The ID of the run's default key-value store. */
  public String getDefaultKeyValueStoreId() {
    return defaultKeyValueStoreId;
  }

  /** The ID of the run's default request queue. */
  public String getDefaultRequestQueueId() {
    return defaultRequestQueueId;
  }

  /** The URL of the run's container (for live access). */
  public String getContainerUrl() {
    return containerUrl;
  }

  /** Whether the run has reached a terminal (finished) status. */
  public boolean isTerminal() {
    return Statuses.isTerminal(status);
  }
}
