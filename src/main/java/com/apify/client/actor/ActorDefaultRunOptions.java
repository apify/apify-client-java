package com.apify.client.actor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Default configuration options applied to an {@link Actor}'s runs unless overridden. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ActorDefaultRunOptions {
  private String build;
  private Long timeoutSecs;
  private Long memoryMbytes;
  private Boolean restartOnError;

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
}
