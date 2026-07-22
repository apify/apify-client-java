package com.apify.client.task;

import com.apify.client.ApifyResource;

/** The stored run configuration of a {@link Task} (the defaults applied when it is started). */
public final class TaskOptions extends ApifyResource {
  private String build;
  private Long timeoutSecs;
  private Long memoryMbytes;
  private Boolean restartOnError;

  /** The tag or number of the build the task runs by default. */
  public String getBuild() {
    return build;
  }

  /** The default run timeout in seconds. */
  public Long getTimeoutSecs() {
    return timeoutSecs;
  }

  /** The default memory in megabytes allocated for the run. */
  public Long getMemoryMbytes() {
    return memoryMbytes;
  }

  /** Whether the run is restarted by default if it fails. */
  public Boolean getRestartOnError() {
    return restartOnError;
  }
}
