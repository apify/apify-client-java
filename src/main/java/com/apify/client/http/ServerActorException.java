package com.apify.client.http;

/**
 * Thrown when a call to an Actor's server cannot be made, since the Actor is not a Server Actor, or
 * since the build that serves its server interface does not exist. The API was reached, but it has
 * nothing to send the call to, which is why this is neither an {@link ApifyApiException} nor an
 * {@link ApifyTransportException}.
 *
 * <p>An Actor that does not exist at all is reported as a {@link NotFoundError}, as for any other
 * call.
 */
public final class ServerActorException extends ApifyClientException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception with a message that names the Actor or the build. */
  public ServerActorException(String message) {
    super(message);
  }
}
