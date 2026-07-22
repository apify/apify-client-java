package com.apify.client.task;

import com.apify.client.ApifyResource;

/** Usage statistics for a {@link Task}. */
public final class TaskStats extends ApifyResource {
  private long totalRuns;

  /** The total number of runs started from this task. */
  public long getTotalRuns() {
    return totalRuns;
  }
}
