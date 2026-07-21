package com.apify.client.build;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Runtime statistics for a {@link Build}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BuildStats {
  private Long durationMillis;
  private Long runTimeSecs;
  private Double computeUnits;

  /** How long the build took, in milliseconds. */
  public Long getDurationMillis() {
    return durationMillis;
  }

  /** How long the build took, in seconds. */
  public Long getRunTimeSecs() {
    return runTimeSecs;
  }

  /** Compute units consumed by the build. */
  public Double getComputeUnits() {
    return computeUnits;
  }
}
