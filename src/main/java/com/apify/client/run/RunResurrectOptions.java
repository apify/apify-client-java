package com.apify.client.run;

import com.apify.client.internal.QueryParams;

/** Configures {@link RunClient#resurrect(RunResurrectOptions)}. */
public final class RunResurrectOptions {
  private String build;
  private Long memoryMbytes;
  private Long timeoutSecs;
  private Long maxItems;
  private Double maxTotalChargeUsd;
  private Boolean restartOnError;

  /** The tag or number of the build to resurrect with. */
  public RunResurrectOptions build(String build) {
    this.build = build;
    return this;
  }

  /** Memory in megabytes to allocate. */
  public RunResurrectOptions memoryMbytes(Long memoryMbytes) {
    this.memoryMbytes = memoryMbytes;
    return this;
  }

  /** The run timeout in seconds. */
  public RunResurrectOptions timeoutSecs(Long timeoutSecs) {
    this.timeoutSecs = timeoutSecs;
    return this;
  }

  /** Maximum number of dataset items to charge (pay-per-result Actors). */
  public RunResurrectOptions maxItems(Long maxItems) {
    this.maxItems = maxItems;
    return this;
  }

  /** Maximum total charge in USD (pay-per-event Actors). */
  public RunResurrectOptions maxTotalChargeUsd(Double maxTotalChargeUsd) {
    this.maxTotalChargeUsd = maxTotalChargeUsd;
    return this;
  }

  /** If {@code true}, restart the run if it fails. */
  public RunResurrectOptions restartOnError(Boolean restartOnError) {
    this.restartOnError = restartOnError;
    return this;
  }

  void apply(QueryParams q) {
    q.addString("build", build)
        .addLong("memory", memoryMbytes)
        .addLong("timeout", timeoutSecs)
        .addLong("maxItems", maxItems)
        .addDouble("maxTotalChargeUsd", maxTotalChargeUsd)
        .addBool("restartOnError", restartOnError);
  }
}
