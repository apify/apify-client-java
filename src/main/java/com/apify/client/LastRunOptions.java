package com.apify.client;

/**
 * Filters which "last" run the {@link ActorClient#lastRun}/{@link TaskClient#lastRun} accessors
 * resolve to. Leave a field unset to leave that filter unset.
 *
 * <p>{@code origin} is a spec-declared query parameter on the {@code runs/last} endpoints
 * (alongside {@code status}), matching the reference client's {@code lastRun({status, origin})}.
 * The spec also declares {@code waitForFinish} on those endpoints, but the reference client does
 * not expose it on {@code lastRun}, so neither do we.
 */
public final class LastRunOptions {
  private RunStatus status;
  private RunOrigin origin;

  /** Filter by run status (e.g. {@link RunStatus#SUCCEEDED}). */
  public LastRunOptions status(RunStatus status) {
    this.status = status;
    return this;
  }

  /** Filter by how the run was started (e.g. {@link RunOrigin#API}). */
  public LastRunOptions origin(RunOrigin origin) {
    this.origin = origin;
    return this;
  }

  String statusValue() {
    return status == null ? null : status.getWireValue();
  }

  String originValue() {
    return origin == null ? null : origin.getWireValue();
  }
}
