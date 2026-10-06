package com.apify.client.http;

import java.util.Map;

/**
 * An {@link ApifyApiException} for an HTTP {@code 401 Unauthorized} response: no valid API token
 * was supplied.
 */
public final class UnauthorizedError extends ApifyApiException {

  private static final long serialVersionUID = 1L;

  /** Constructs an exception describing a {@code 401} API error response. */
  public UnauthorizedError(
      String type,
      String message,
      int attempt,
      String httpMethod,
      String path,
      Map<String, Object> data) {
    super(401, type, message, attempt, httpMethod, path, data);
  }
}
