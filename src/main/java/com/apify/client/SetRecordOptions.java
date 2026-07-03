package com.apify.client;

/**
 * Write options for {@link KeyValueStoreClient#setRecord(String, byte[], String,
 * SetRecordOptions)}, mirroring the reference client's {@code timeoutSecs}/{@code
 * doNotRetryTimeouts}.
 */
public final class SetRecordOptions {
  private Long timeoutSecs;
  private Boolean doNotRetryTimeouts;

  /**
   * Per-request timeout for the upload, in seconds. Use it to shorten the wait for this upload;
   * defaults to (and is capped at) the client's configured overall request timeout, so a value
   * larger than that timeout has no effect.
   */
  public SetRecordOptions timeoutSecs(Long timeoutSecs) {
    this.timeoutSecs = timeoutSecs;
    return this;
  }

  /** If {@code true}, do not retry the upload when it fails with a request timeout. */
  public SetRecordOptions doNotRetryTimeouts(Boolean doNotRetryTimeouts) {
    this.doNotRetryTimeouts = doNotRetryTimeouts;
    return this;
  }

  Long timeoutSecsValue() {
    return timeoutSecs;
  }

  boolean doNotRetryTimeoutsValue() {
    return Boolean.TRUE.equals(doNotRetryTimeouts);
  }
}
