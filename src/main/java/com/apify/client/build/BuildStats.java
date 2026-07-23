package com.apify.client.build;

import com.apify.client.ApifyResource;

/** Runtime statistics for a {@link Build}. */
public final class BuildStats extends ApifyResource {
  private Long durationMillis;
  private Long runTimeSecs;
  private Double computeUnits;
  private Long imageSizeBytes;

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

  /** The size of the built Docker image, in bytes, if known. */
  public Long getImageSizeBytes() {
    return imageSizeBytes;
  }
}
