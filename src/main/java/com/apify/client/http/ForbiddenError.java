package com.apify.client.http;

import java.util.Map;

/**
 * An {@link ApifyApiException} for an HTTP {@code 403 Forbidden} response: the API token is valid
 * but lacks permission for the requested operation.
 */
public final class ForbiddenError extends ApifyApiException {

  private static final long serialVersionUID = 1L;

  /** Constructs an exception describing a {@code 403} API error response. */
  public ForbiddenError(
      String type,
      String message,
      int attempt,
      String httpMethod,
      String path,
      Map<String, Object> data) {
    super(403, type, message, attempt, httpMethod, path, data);
  }
}
