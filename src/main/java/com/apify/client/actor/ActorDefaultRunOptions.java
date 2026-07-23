package com.apify.client.actor;

import com.apify.client.ApifyResource;

/** Default configuration options applied to an {@link Actor}'s runs unless overridden. */
public final class ActorDefaultRunOptions extends ApifyResource {
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
