package com.apify.client.run;

import com.apify.client.actor.ActorClient;
import com.apify.client.task.TaskClient;

/**
 * Filters which "last" run the {@link ActorClient#lastRun}/{@link TaskClient#lastRun} accessors
 * resolve to. Leave a field unset to leave that filter unset.
 *
 * <p>{@code waitForFinish}, which the endpoints also accept, is not exposed.
 */
public final class LastRunOptions {
  private String status;
  private String origin;

  /** Filter by run status (e.g. {@code "SUCCEEDED"}, {@code "FAILED"}, {@code "RUNNING"}). */
  public LastRunOptions status(String status) {
    this.status = status;
    return this;
  }

  /**
   * Filter by how the run was started (e.g. {@code "DEVELOPMENT"}, {@code "WEB"}, {@code "API"}).
   */
  public LastRunOptions origin(String origin) {
    this.origin = origin;
    return this;
  }

  String statusValue() {
    return status;
  }

  String originValue() {
    return origin;
  }
}
