package com.apify.client;

/**
 * Filters which "last" run the {@link ActorClient#lastRun}/{@link TaskClient#lastRun} accessors
 * resolve to. Leave a field unset to leave that filter unset.
 *
 * <p>{@code origin} is an Apify-platform convenience exposed by the reference client but not
 * documented as a query parameter in the OpenAPI spec; it is included for parity, threaded to the
 * same {@code runs/last} endpoint.
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
