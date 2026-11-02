package com.apify.client.actor;

import com.apify.client.ApifyResource;

/** Default configuration options applied to an {@link Actor}'s runs unless overridden. */
public final class ActorDefaultRunOptions extends ApifyResource {
  private String build;
  private Long timeoutSecs;
  private Long memoryMbytes;
  private Boolean restartOnError;
  private Double maxTotalChargeUsd;
  private Boolean isMaxTotalChargeUsdSetByUser;

  /** The tag or number of the build run by default. */
  public String getBuild() {
    return build;
  }

  /** The default run timeout, in seconds. */
  public Long getTimeoutSecs() {
    return timeoutSecs;
  }

  /** The default memory allocation, in megabytes. */
  public Long getMemoryMbytes() {
    return memoryMbytes;
  }

  /** Whether a run is restarted by default if it fails. */
  public Boolean getRestartOnError() {
    return restartOnError;
  }

  /** The default maximum total charge in USD for a pay-per-event run. */
  public Double getMaxTotalChargeUsd() {
    return maxTotalChargeUsd;
  }

  /** Whether {@link #getMaxTotalChargeUsd()} was set by the user rather than defaulted. */
  public Boolean getIsMaxTotalChargeUsdSetByUser() {
    return isMaxTotalChargeUsdSetByUser;
  }
}
