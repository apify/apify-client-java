package com.apify.client.http;

/**
 * Common base of every exception this client throws for a failed API call.
 *
 * <p>It is unchecked (a {@link RuntimeException}), matching the reference JavaScript client's
 * {@code Error}-based, unchecked error model, so callers are not forced to wrap every call in a
 * {@code try}/{@code catch}. Two concrete subtypes cover the two ways a call can fail:
 *
 * <ul>
 *   <li>{@link ApifyApiException} — the request reached the API, which answered with a non-success
 *       status code.
 *   <li>{@link ApifyTransportException} — the request never produced an API response at all
 *       (connection failure, DNS, timeout, or a local failure while preparing the request/response,
 *       e.g. compression).
 * </ul>
 *
 * Catch this common type to handle both failure modes uniformly, or catch a specific subtype to
 * handle one of them differently.
 */
public abstract class ApifyClientException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  ApifyClientException(String message) {
    super(message);
  }

  ApifyClientException(Throwable cause) {
    super(cause);
  }
}
