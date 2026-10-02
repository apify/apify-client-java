package com.apify.client.http;

import java.util.Map;

/**
 * An {@link ApifyApiException} for an HTTP {@code 400 Bad Request} response: the request itself was
 * malformed or failed server-side validation.
 */
public final class InvalidRequestError extends ApifyApiException {

  private static final long serialVersionUID = 1L;

  /** Constructs an exception describing a {@code 400} API error response. */
  public InvalidRequestError(
      String type,
      String message,
      int attempt,
      String httpMethod,
      String path,
      Map<String, Object> data) {
    super(400, type, message, attempt, httpMethod, path, data);
  }
}
