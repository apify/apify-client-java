package com.apify.client.http;

import java.util.Map;

/**
 * An {@link ApifyApiException} for an HTTP {@code 409 Conflict} response: the request conflicts
 * with the resource's current state (e.g. a duplicate name).
 */
public final class ConflictError extends ApifyApiException {

  private static final long serialVersionUID = 1L;

  /** Constructs an exception describing a {@code 409} API error response. */
  public ConflictError(
      String type,
      String message,
      int attempt,
      String httpMethod,
      String path,
      Map<String, Object> data) {
    super(409, type, message, attempt, httpMethod, path, data);
  }
}
