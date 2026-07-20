package com.apify.client.http;

import java.util.Collections;
import java.util.Map;

/**
 * Thrown for HTTP requests that reach the Apify API but receive a non-success status code.
 *
 * <p>It mirrors the {@code ApifyApiError} of the reference JavaScript client and exposes the parsed
 * error {@link #getType() type}, the human-readable {@link #getMessage() message}, the HTTP {@link
 * #getStatusCode() status code}, the number of the final {@link #getAttempt() attempt}, and the
 * request {@link #getHttpMethod() method}/{@link #getPath() path}.
 *
 * <p>It is an unchecked exception so callers are not forced to wrap every call; recover from it
 * with a normal {@code try}/{@code catch} where relevant.
 */
public class ApifyApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int statusCode;
  private final String type;
  private final int attempt;
  private final String httpMethod;
  private final String path;
  private final transient Map<String, Object> data;

  ApifyApiException(
      int statusCode,
      String type,
      String message,
      int attempt,
      String httpMethod,
      String path,
      Map<String, Object> data) {
    super(message);
    this.statusCode = statusCode;
    this.type = type;
    this.attempt = attempt;
    this.httpMethod = httpMethod;
    this.path = path;
    this.data = data;
  }

  /** The HTTP status code of the error response. */
  public int getStatusCode() {
    return statusCode;
  }

  /** The machine-readable error type returned by the API (e.g. {@code "record-not-found"}). */
  public String getType() {
    return type;
  }

  /** The number of the API call attempt that produced this error (1-based). */
  public int getAttempt() {
    return attempt;
  }

  /** The HTTP method of the API call (e.g. {@code "GET"}, {@code "POST"}). */
  public String getHttpMethod() {
    return httpMethod;
  }

  /** The path of the API endpoint (URL excluding origin). */
  public String getPath() {
    return path;
  }

  /**
   * Additional structured data provided by the API about the error, if any (may be {@code null}).
   */
  public Map<String, Object> getData() {
    return data == null ? null : Collections.unmodifiableMap(data);
  }

  @Override
  public String getMessage() {
    String errType = (type == null || type.isEmpty()) ? "unknown" : type;
    return String.format(
        "apify API error (status %d, type %s): %s", statusCode, errType, super.getMessage());
  }
}
