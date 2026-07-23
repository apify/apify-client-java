package com.apify.client.http;

/**
 * Thrown when a call never produced an API response: a network-level failure (connection refused,
 * DNS, timeout) reported by the {@link HttpTransport}, or a local failure while preparing the
 * request or consuming the response (e.g. request-body compression) that prevented the exchange
 * from happening at all.
 *
 * <p>Retried the same way as a retryable HTTP status (see {@link RetryConfig}), up to the
 * configured number of attempts, unless the caller opted out of retrying timeouts for that call.
 *
 * @see ApifyApiException for failures where the API did respond, with a non-success status.
 */
public final class ApifyTransportException extends ApifyClientException {

  private static final long serialVersionUID = 1L;

  public ApifyTransportException(Throwable cause) {
    super(cause);
  }

  /**
   * Reports whether this transport failure was caused by the request timing out, as opposed to some
   * other network failure. Transport-implementation-agnostic: it recognizes {@link
   * HttpTimeoutException}, the {@link HttpTransport} contract's own timeout signal, not any
   * specific transport implementation's exception type.
   */
  public boolean isTimeout() {
    return getCause() instanceof HttpTimeoutException;
  }
}
