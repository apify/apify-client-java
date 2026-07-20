package com.apify.client;

/**
 * Thrown when a request fails at the transport level — connection refused, DNS failure, a request
 * timeout — before any HTTP response is received from the Apify API.
 *
 * <p>This is distinct from {@link ApifyApiException}, which is thrown when the API <em>does</em>
 * respond, but with a non-success status. Transport failures are retried the same way as retryable
 * HTTP statuses (see the client's retry/timeout configuration), and this exception is thrown only
 * once the retry budget is exhausted (or immediately, for a timeout, when the caller has opted out
 * of retrying timeouts).
 *
 * <p>It is an unchecked exception, consistent with {@link ApifyApiException}, so callers are not
 * forced to wrap every call.
 */
public class ApifyTransportException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ApifyTransportException(Throwable cause) {
    super(cause);
  }
}
