package com.apify.client.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Usage statistics for a {@link Task}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class TaskStats {
  private long totalRuns;

  /** The total number of runs started from this task. */
  public long getTotalRuns() {
    return totalRuns;
  }
}
