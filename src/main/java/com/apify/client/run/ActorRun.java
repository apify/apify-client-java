package com.apify.client.run;

import com.apify.client.ApifyResource;
import com.apify.client.internal.Statuses;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/** A single execution of an Actor. */
public final class ActorRun extends ApifyResource {
  private String id;
  private String actId;
  private String actorTaskId;
  private String userId;
  private String status;
  private String statusMessage;
  private Instant startedAt;
  private Instant finishedAt;
  private String buildId;
  private String defaultDatasetId;
  private String defaultKeyValueStoreId;
  private String defaultRequestQueueId;
  private String containerUrl;
  private String generalAccess;
  private Map<String, Long> chargedEventCounts;
  private JsonNode pricingInfo;
  private ActorRunUsage usage;
  private ActorRunUsage usageUsd;
  private Double usageTotalUsd;
  private ActorRunStats stats;
  private ActorRunOptions options;
  private ActorRunMeta meta;
  private String buildNumber;
  private Integer exitCode;
  private Boolean isContainerServerReady;
  private String gitBranchName;
  private JsonNode storageIds;

  /** The unique run ID. */
  public String getId() {
    return id;
  }

  /** The ID of the Actor that produced this run. */
  public String getActId() {
    return actId;
  }

  /** The ID of the task that started this run, if any. */
  public String getActorTaskId() {
    return actorTaskId;
  }

  /** The ID of the user who owns the run. */
  public String getUserId() {
    return userId;
  }

  /**
   * The current run status. One of the eight {@code ActorJobStatus} values: {@code READY}, {@code
   * RUNNING}, {@code SUCCEEDED}, {@code FAILED}, {@code TIMING-OUT}, {@code TIMED-OUT}, {@code
   * ABORTING}, {@code ABORTED}.
   */
  public String getStatus() {
    return status;
  }

  /** An optional human-readable status message. */
  public String getStatusMessage() {
    return statusMessage;
  }

  /** When the run started. */
  public Instant getStartedAt() {
    return startedAt;
  }

  /** When the run finished (absent while still running). */
  public Instant getFinishedAt() {
    return finishedAt;
  }

  /** The ID of the build used for the run. */
  public String getBuildId() {
    return buildId;
  }

  /** The ID of the run's default dataset. */
  public String getDefaultDatasetId() {
    return defaultDatasetId;
  }

  /** The ID of the run's default key-value store. */
  public String getDefaultKeyValueStoreId() {
    return defaultKeyValueStoreId;
  }

  /** The ID of the run's default request queue. */
  public String getDefaultRequestQueueId() {
    return defaultRequestQueueId;
  }

  /** The URL of the run's container (for live access). */
  public String getContainerUrl() {
    return containerUrl;
  }

  /**
   * Who can access this run without owning it (e.g. {@code "ANYONE_WITH_ID_CAN_READ"}, {@code
   * "RESTRICTED"}), or {@code null} to follow the account/Actor default.
   */
  public String getGeneralAccess() {
    return generalAccess;
  }

  /**
   * For a pay-per-event Actor, how many times each event type was charged during this run (event
   * type -> count); {@code null} for Actors that are not pay-per-event.
   */
  public Map<String, Long> getChargedEventCounts() {
    return chargedEventCounts == null ? null : Collections.unmodifiableMap(chargedEventCounts);
  }

  /**
   * Pricing information for the Actor this run belongs to, as raw JSON (its shape depends on the
   * Actor's pricing model: pay-per-event, pay-per-result, flat monthly, or free).
   */
  public JsonNode getPricingInfo() {
    return pricingInfo;
  }

  /** Resource usage consumed by the run so far, broken down by billable unit. */
  public ActorRunUsage getUsage() {
    return usage;
  }

  /** As {@link #getUsage()}, converted to USD; only present where {@link #getUsage()} is. */
  public ActorRunUsage getUsageUsd() {
    return usageUsd;
  }

  /**
   * Total cost in USD for this run (what the caller actually pays): platform usage and/or event
   * costs, depending on the Actor's pricing model. For a run the caller does not own, only
   * available (non-{@code null}) for pay-per-event Actors, and only covers event costs.
   */
  public Double getUsageTotalUsd() {
    return usageTotalUsd;
  }

  /** Runtime resource-consumption and performance statistics for the run. */
  public ActorRunStats getStats() {
    return stats;
  }

  /** The run configuration actually applied (may differ from what the caller requested). */
  public ActorRunOptions getOptions() {
    return options;
  }

  /** Metadata about how the run was initiated. */
  public ActorRunMeta getMeta() {
    return meta;
  }

  /** The build number (semver-like, e.g. {@code "0.0.36"}) of the build used for this run. */
  public String getBuildNumber() {
    return buildNumber;
  }

  /** The exit code of the run's process, once it has finished; {@code null} while running. */
  public Integer getExitCode() {
    return exitCode;
  }

  /** Whether the run container's HTTP server is ready to accept requests. */
  public Boolean isContainerServerReady() {
    return isContainerServerReady;
  }

  /** The name of the git branch the Actor build was built from, if applicable. */
  public String getGitBranchName() {
    return gitBranchName;
  }

  /**
   * A map of aliased storage IDs associated with this run, grouped by storage type ({@code
   * datasets}/{@code keyValueStores}/{@code requestQueues}), each with at least a {@code "default"}
   * entry matching {@link #getDefaultDatasetId()}/{@link #getDefaultKeyValueStoreId()}/{@link
   * #getDefaultRequestQueueId()}. Returned as raw JSON since the set of aliases beyond {@code
   * "default"} is open-ended.
   */
  public JsonNode getStorageIds() {
    return storageIds;
  }

  /** Whether the run has reached a terminal (finished) status. */
  public boolean isTerminal() {
    return Statuses.isTerminal(status);
  }
}
