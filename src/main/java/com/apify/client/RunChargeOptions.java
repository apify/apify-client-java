package com.apify.client;

/** Configures {@link RunClient#charge(RunChargeOptions)}. */
public final class RunChargeOptions {
  private String eventName;
  private Long count;
  private String idempotencyKey;

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
   * "{runId}-{eventName}-{timestampMillis}-{random}"}, matching the reference client.
   */
  public RunChargeOptions idempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
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
}
