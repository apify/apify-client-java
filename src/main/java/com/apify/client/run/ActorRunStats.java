package com.apify.client.run;

import com.apify.client.ApifyResource;

/** Runtime resource-consumption and performance metrics for an {@link ActorRun}. */
public final class ActorRunStats extends ApifyResource {
  private long inputBodyLen;
  private long migrationCount;
  private long rebootCount;
  private long restartCount;
  private long resurrectCount;
  private double memAvgBytes;
  private double memMaxBytes;
  private double memCurrentBytes;
  private double cpuAvgUsage;
  private double cpuMaxUsage;
  private double cpuCurrentUsage;
  private long netRxBytes;
  private long netTxBytes;
  private long durationMillis;
  private double runTimeSecs;
  private long metamorph;
  private double computeUnits;

  /** The byte length of the run's input body. */
  public long getInputBodyLen() {
    return inputBodyLen;
  }

  /** How many times the run's container was migrated to a different host. */
  public long getMigrationCount() {
    return migrationCount;
  }

  /** How many times the run's container was rebooted. */
  public long getRebootCount() {
    return rebootCount;
  }

  /** How many times the run was automatically restarted. */
  public long getRestartCount() {
    return restartCount;
  }

  /** How many times the run was resurrected. */
  public long getResurrectCount() {
    return resurrectCount;
  }

  /** Average memory usage, in bytes. */
  public double getMemAvgBytes() {
    return memAvgBytes;
  }

  /** Peak memory usage, in bytes. */
  public double getMemMaxBytes() {
    return memMaxBytes;
  }

  /** Memory usage at the time of the response, in bytes. */
  public double getMemCurrentBytes() {
    return memCurrentBytes;
  }

  /** Average CPU usage percentage. */
  public double getCpuAvgUsage() {
    return cpuAvgUsage;
  }

  /** Peak CPU usage percentage. */
  public double getCpuMaxUsage() {
    return cpuMaxUsage;
  }

  /** CPU usage percentage at the time of the response. */
  public double getCpuCurrentUsage() {
    return cpuCurrentUsage;
  }

  /** Bytes received over the network. */
  public long getNetRxBytes() {
    return netRxBytes;
  }

  /** Bytes sent over the network. */
  public long getNetTxBytes() {
    return netTxBytes;
  }

  /** The run's duration, in milliseconds. */
  public long getDurationMillis() {
    return durationMillis;
  }

  /** The run's duration, in seconds. */
  public double getRunTimeSecs() {
    return runTimeSecs;
  }

  /** How many times the run was metamorphed into another Actor. */
  public long getMetamorph() {
    return metamorph;
  }

  /** Compute units consumed by the run. */
  public double getComputeUnits() {
    return computeUnits;
  }
}
