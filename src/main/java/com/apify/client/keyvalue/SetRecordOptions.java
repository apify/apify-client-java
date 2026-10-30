package com.apify.client.keyvalue;

import com.apify.client.http.Timeout;

/**
 * Write options for {@link KeyValueStoreClient#setRecord(String, byte[], String,
 * SetRecordOptions)}, mirroring the reference client's {@code timeoutSecs}/{@code
 * doNotRetryTimeouts}/{@code contentEncoding}.
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
   * header (trimmed first; it must not be blank). Set it to upload a pre-compressed value (e.g. a
   * gzipped file read from disk): the client then sends the bytes as they are and does not compress
   * them again. The API accepts {@code gzip}, {@code br}, {@code deflate}, and {@code identity}
   * (matched case-insensitively) and stores the record exactly as uploaded; since this client does
   * not advertise support for a compressed response ({@code Accept-Encoding}), a later {@link
   * KeyValueStoreClient#getRecord(String) getRecord} transparently receives the decompressed value
   * rather than the stored compressed bytes. Passing {@code identity} turns the client's own
   * automatic compression off for this upload without changing how the client is configured.
   *
   * <p>Unlike the reference JS client, this has no corresponding type check: {@code value} here is
   * always raw bytes (never a string or an object the client would serialize to JSON), so there is
   * no non-binary case to reject.
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
