package com.apify.client.http;

import java.util.Map;

/**
 * An {@link ApifyApiException} for an HTTP {@code 429 Too Many Requests} response. The client
 * already retries this status internally (see {@code RetryConfig}); it reaches the caller only once
 * every retry has been exhausted.
 */
public final class RateLimitError extends ApifyApiException {

  private static final long serialVersionUID = 1L;

  /** Constructs an exception describing a {@code 429} API error response. */
  public RateLimitError(
      String type,
      String message,
      int attempt,
      String httpMethod,
      String path,
      Map<String, Object> data) {
    super(429, type, message, attempt, httpMethod, path, data);
  }
}
