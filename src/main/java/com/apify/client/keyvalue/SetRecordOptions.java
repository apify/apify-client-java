package com.apify.client.keyvalue;

import com.apify.client.http.Timeout;

/**
 * Write options for {@link KeyValueStoreClient#setRecord(String, byte[], String,
 * SetRecordOptions)}: the timeout, whether to retry timeouts, and the content encoding.
 */
public final class SetRecordOptions {
  private Timeout timeout;
  private Boolean doNotRetryTimeouts;
  private String contentEncoding;

  /**
   * Per-request timeout for the upload, in seconds. Shorthand for {@link #timeout(Timeout)
   * timeout(Timeout.ofSeconds(timeoutSecs))}; a {@code null} or non-positive value leaves the
   * default.
   */
  public SetRecordOptions timeoutSecs(Long timeoutSecs) {
    this.timeout = timeoutSecs == null || timeoutSecs <= 0 ? null : Timeout.ofSeconds(timeoutSecs);
    return this;
  }

  /**
   * Timeout of the upload request: a tier, an exact duration or {@link Timeout#NO_TIMEOUT}.
   * Defaults to {@link Timeout#LONG}. A value above the client's {@code timeoutMax} is capped at
   * it.
   */
  public SetRecordOptions timeout(Timeout timeout) {
    this.timeout = timeout;
    return this;
  }

  /** If {@code true}, do not retry the upload when it fails with a request timeout. */
  public SetRecordOptions doNotRetryTimeouts(Boolean doNotRetryTimeouts) {
    this.doNotRetryTimeouts = doNotRetryTimeouts;
    return this;
  }

  /**
   * Encoding already applied to the record's {@code value}, sent as the {@code Content-Encoding}
   * header (trimmed; not blank). Use it to upload a pre-compressed value: the client sends the
   * bytes as they are and does not compress them again. The API accepts {@code gzip}, {@code br},
   * {@code zstd}, {@code deflate} and {@code identity} (case-insensitive) and stores the record as
   * uploaded. Since the client sends no {@code Accept-Encoding}, {@link
   * KeyValueStoreClient#getRecord(String) getRecord} returns the decompressed value. {@code
   * identity} turns off the client's own compression for this upload.
   */
  public SetRecordOptions contentEncoding(String contentEncoding) {
    this.contentEncoding = contentEncoding;
    return this;
  }

  Timeout timeoutValue() {
    return timeout;
  }

  boolean doNotRetryTimeoutsValue() {
    return Boolean.TRUE.equals(doNotRetryTimeouts);
  }

  String contentEncodingValue() {
    return contentEncoding;
  }
}
