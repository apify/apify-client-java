package com.apify.client.http;

/**
 * Common base of every exception this client throws for a failed API call.
 *
 * <p>It is unchecked. Three subtypes cover the ways a call can fail:
 *
 * <ul>
 *   <li>{@link ApifyApiException} — the request reached the API, which answered with a non-success
 *       status code.
 *   <li>{@link ApifyTransportException} — the request produced no API response (connection failure,
 *       DNS, timeout, or a local failure such as compression).
 *   <li>{@link ServerActorException} — the call to an Actor's server cannot be made, because the
 *       Actor is not a Server Actor or the build serving its server interface does not exist.
 * </ul>
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
