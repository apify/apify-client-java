package com.apify.client.run;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The configuration options actually applied to an {@link ActorRun} (as opposed to {@link
 * com.apify.client.actor.ActorStartOptions}, which is what a caller requested and may differ, e.g.
 * when a requested value falls back to a task/Actor default).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ActorRunOptions {
  private String build;
  private long timeoutSecs;
  private long memoryMbytes;
  private long diskMbytes;
  private Long maxItems;
  private Double maxTotalChargeUsd;
  private Boolean restartOnError;

  /** The tag or number of the build that was run. */
  public String getBuild() {
    return build;
  }

  /** The applied run timeout, in seconds. */
  public long getTimeoutSecs() {
    return timeoutSecs;
  }

  /** The applied memory allocation, in megabytes. */
  public long getMemoryMbytes() {
    return memoryMbytes;
  }

  /** The applied disk allocation, in megabytes. */
  public long getDiskMbytes() {
    return diskMbytes;
  }

  /** The applied cap on dataset items charged (pay-per-result Actors), if any. */
  public Long getMaxItems() {
    return maxItems;
  }

  /** The applied cap on total charge in USD (pay-per-event Actors), if any. */
  public Double getMaxTotalChargeUsd() {
    return maxTotalChargeUsd;
  }

  /** Whether the run is restarted if it fails. */
  public Boolean getRestartOnError() {
    return restartOnError;
  }
}
