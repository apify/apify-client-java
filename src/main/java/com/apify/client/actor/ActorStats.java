package com.apify.client.actor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** Usage and activity statistics for an {@link Actor}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ActorStats {
  private Long totalBuilds;
  private Long totalRuns;
  private Long totalUsers;
  private Long totalUsers7Days;
  private Long totalUsers30Days;
  private Long totalUsers90Days;
  private Long totalMetamorphs;
  private Instant lastRunStartedAt;

  /** Total number of builds created for this Actor. */
  public Long getTotalBuilds() {
    return totalBuilds;
  }

  /** Total number of times this Actor has been run. */
  public Long getTotalRuns() {
    return totalRuns;
  }

  /** Total number of unique users who have run this Actor. */
  public Long getTotalUsers() {
    return totalUsers;
  }

  /** Number of unique users in the last 7 days. */
  public Long getTotalUsers7Days() {
    return totalUsers7Days;
  }

  /** Number of unique users in the last 30 days. */
  public Long getTotalUsers30Days() {
    return totalUsers30Days;
  }

  /** Number of unique users in the last 90 days. */
  public Long getTotalUsers90Days() {
    return totalUsers90Days;
  }

  /** Total number of times this Actor was used via metamorph. */
  public Long getTotalMetamorphs() {
    return totalMetamorphs;
  }

  /** When the last run of this Actor was started. */
  public Instant getLastRunStartedAt() {
    return lastRunStartedAt;
  }
}
