package com.apify.client.run;

/** Configures {@link RunClient#charge(RunChargeOptions)}. */
public final class RunChargeOptions {
  private String eventName;
  private Long count;
  private String idempotencyKey;
  private String requestId;

  /** Creates options for the given (required) event name. */
  public RunChargeOptions(String eventName) {
    this.eventName = eventName;
  }

  /** The name of the event to charge for. Required. */
  public RunChargeOptions eventName(String eventName) {
    this.eventName = eventName;
    return this;
  }

  /** The number of times to charge the event (defaults to 1). */
  public RunChargeOptions count(Long count) {
    this.count = count;
    return this;
  }

  /**
   * A key that deduplicates the charge across retries. If unset, one is auto-generated as {@code
   * "{runId}-{eventName}-{timestampMillis}-{random}"}.
   */
  public RunChargeOptions idempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
    return this;
  }

  /**
   * The ID of the Server Actor request being charged, taken from the {@code X-Actor-Request-ID}
   * header of the incoming request. Required for multi-tenant Server Actor runs, where it
   * identifies the request the charge belongs to. Runs of single-tenant Server Actors ignore it.
   */
  public RunChargeOptions requestId(String requestId) {
    this.requestId = requestId;
    return this;
  }

  String eventNameValue() {
    return eventName;
  }

  long countValue() {
    return count != null ? count : 1;
  }

  String idempotencyKeyValue() {
    return idempotencyKey;
  }

  String requestIdValue() {
    return requestId;
  }
}
