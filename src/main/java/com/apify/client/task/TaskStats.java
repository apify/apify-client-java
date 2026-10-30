package com.apify.client.task;

import com.apify.client.ApifyResource;
import java.time.Instant;

/** Usage statistics for a {@link Task}. */
public final class TaskStats extends ApifyResource {
  private long totalRuns;
  private Instant lastRunStartedAt;

  /** The total number of runs started from this task. */
  public long getTotalRuns() {
    return totalRuns;
  }

  /** When the most recent run of the task started. */
  public Instant getLastRunStartedAt() {
    return lastRunStartedAt;
  }
}
