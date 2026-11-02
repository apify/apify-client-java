package com.apify.client.actor;

import com.apify.client.ApifyResource;

/** Standby-mode configuration for an Actor, or for a task that overrides its Actor's defaults. */
public final class ActorStandby extends ApifyResource {
  private Boolean isEnabled;
  private String build;
  private Long desiredRequestsPerActorRun;
  private Boolean disableStandbyFieldsOverride;
  private Long idleTimeoutSecs;
  private Long maxRequestsPerActorRun;
  private Long memoryMbytes;
  private Boolean shouldPassActorInput;
  private String tenancy;
  private Boolean isConsoleAuthEnabled;
  private Boolean isTokenlessEnabled;

  /**
   * Whether standby mode is enabled. Only present on {@link Actor#getActorStandby()} (the API
   * models it as an intersection type there); always {@code null} on {@link
   * com.apify.client.task.Task#getActorStandby()}, which reuses the same shape for its per-task
   * override without this field.
   */
  public Boolean getIsEnabled() {
    return isEnabled;
  }

  /** The tag or number of the build used to serve standby requests. */
  public String getBuild() {
    return build;
  }

  /** The desired number of concurrent requests handled by a single standby run. */
  public Long getDesiredRequestsPerActorRun() {
    return desiredRequestsPerActorRun;
  }

  /** Whether a task-level override of these standby fields is disabled. */
  public Boolean getDisableStandbyFieldsOverride() {
    return disableStandbyFieldsOverride;
  }

  /** Seconds of inactivity after which an idle standby run is shut down. */
  public Long getIdleTimeoutSecs() {
    return idleTimeoutSecs;
  }

  /** The maximum number of concurrent requests a single standby run handles. */
  public Long getMaxRequestsPerActorRun() {
    return maxRequestsPerActorRun;
  }

  /** Memory in megabytes allocated to each standby run. */
  public Long getMemoryMbytes() {
    return memoryMbytes;
  }

  /** Whether the incoming HTTP request is passed as the standby run's input. */
  public Boolean getShouldPassActorInput() {
    return shouldPassActorInput;
  }

  /**
   * Who the Server Actor serves: {@code SINGLE_TENANT} (a private instance per caller) or a {@code
   * MULTI_TENANT_*} pool.
   */
  public String getTenancy() {
    return tenancy;
  }

  /** Whether Console authentication is enabled for the standby URL. */
  public Boolean getIsConsoleAuthEnabled() {
    return isConsoleAuthEnabled;
  }

  /** Whether requests to the standby URL may omit the API token. */
  public Boolean getIsTokenlessEnabled() {
    return isTokenlessEnabled;
  }
}
