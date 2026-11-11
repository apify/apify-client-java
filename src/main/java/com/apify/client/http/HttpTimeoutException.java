package com.apify.client.http;

/**
 * Signals that an {@link HttpTransport} gave up on a request because it exceeded its timeout, as
 * opposed to some other transport failure (connection refused, DNS, ...).
 *
 * <p>A transport that can tell a timeout from other failures should complete its future
 * exceptionally with this, so the client can apply {@code doNotRetryTimeouts}. A transport that
 * cannot may fail with a plain {@link RuntimeException}; the call is then always eligible for a
 * retry.
 */
public class HttpTimeoutException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public HttpTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
