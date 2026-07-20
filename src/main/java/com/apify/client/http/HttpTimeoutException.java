package com.apify.client.http;

import java.io.IOException;

/**
 * Signals that an {@link HttpTransport} gave up on a request because it exceeded its timeout, as
 * opposed to some other transport failure (connection refused, DNS, ...).
 *
 * <p>Part of the {@link HttpTransport} contract, not tied to any specific backend implementation:
 * an implementation that can distinguish "timed out" from other I/O failures should throw this
 * (wrapping or in place of the underlying exception) so the client can apply {@code
 * doNotRetryTimeouts} correctly. A backend that cannot tell the two apart may throw a plain {@link
 * IOException} instead; the call is then simply always eligible for a retry.
 */
public class HttpTimeoutException extends IOException {

  private static final long serialVersionUID = 1L;

  public HttpTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
