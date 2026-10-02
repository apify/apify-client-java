package com.apify.client.http;

import java.util.Map;

/**
 * An {@link ApifyApiException} for an HTTP {@code 5xx} response: the API itself failed. The client
 * already retries these statuses internally (see {@code RetryConfig}); it reaches the caller only
 * once every retry has been exhausted.
 */
public final class ServerError extends ApifyApiException {

  private static final long serialVersionUID = 1L;

  /** Constructs an exception describing a {@code 5xx} API error response. */
  public ServerError(
      int statusCode,
      String type,
      String message,
      int attempt,
      String httpMethod,
      String path,
      Map<String, Object> data) {
    super(statusCode, type, message, attempt, httpMethod, path, data);
  }
}
