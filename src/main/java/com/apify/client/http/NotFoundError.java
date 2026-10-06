package com.apify.client.http;

import java.util.Map;

/**
 * An {@link ApifyApiException} for an HTTP {@code 404 Not Found} response: the addressed resource
 * does not exist (or the token cannot see it).
 *
 * <p>Most resource clients swallow this into an empty {@link java.util.Optional}/no-op when a
 * resource is addressed by an explicit id (see each client's {@code get()}/{@code delete()}
 * Javadoc); it reaches the caller as this exception everywhere the 404 is ambiguous, e.g. a client
 * chained off a run or build without an id of its own ({@code run.dataset()}, {@code build.log()},
 * ...), where the missing resource may be the parent rather than the sub-resource.
 */
public final class NotFoundError extends ApifyApiException {

  private static final long serialVersionUID = 1L;

  /** Constructs an exception describing a {@code 404} API error response. */
  public NotFoundError(
      String type,
      String message,
      int attempt,
      String httpMethod,
      String path,
      Map<String, Object> data) {
    super(404, type, message, attempt, httpMethod, path, data);
  }
}
