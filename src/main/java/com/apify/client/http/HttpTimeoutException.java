package com.apify.client.http;

/**
 * Signals that an {@link HttpTransport} gave up on a request because it exceeded its timeout, as
 * opposed to some other transport failure (connection refused, DNS, ...).
 *
 * <p>Part of the {@link HttpTransport} contract, not tied to any specific transport implementation:
 * an implementation that can distinguish "timed out" from other failures should complete its future
 * exceptionally with this (wrapping or in place of the underlying exception) so the client can
 * apply {@code doNotRetryTimeouts} correctly. A transport implementation that cannot tell the two
 * apart may fail with a plain {@link RuntimeException} instead; the call is then simply always
 * eligible for a retry.
 *
 * <p>Unchecked, matching the rest of the client's async transport contract: a {@link
 * java.util.concurrent.CompletableFuture} can only be completed exceptionally with a {@link
 * Throwable}, and every other exception this client throws is already unchecked (see {@link
 * ApifyClientException}).
 */
public class HttpTimeoutException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public HttpTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
