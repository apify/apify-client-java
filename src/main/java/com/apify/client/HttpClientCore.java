package com.apify.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The orchestrating HTTP client shared by every resource client. It owns the backend, the optional
 * API token, the {@code User-Agent}, and the retry/timeout policy, and applies them to every
 * request. Internal to the client; safe for concurrent use.
 */
final class HttpClientCore {

  /** Status returned when the per-resource rate limit is hit. */
  private static final int RATE_LIMIT_EXCEEDED = 429;

  /** Statuses at or above this value are treated as retryable internal server errors. */
  private static final int MIN_SERVER_ERROR = 500;

  /** Responses with status below this value are treated as success. */
  static final int MAX_SUCCESS_STATUS = 300;

  /** Exponential-backoff multiplier applied to the inter-retry delay after each attempt. */
  private static final int BACKOFF_FACTOR = 2;

  private final HttpBackend backend;
  private final String token;
  private final String userAgent;
  private final RetryConfig retry;

  HttpClientCore(HttpBackend backend, String token, String userAgent, RetryConfig retry) {
    this.backend = backend;
    this.token = token;
    this.userAgent = userAgent;
    this.retry = retry;
  }

  String userAgent() {
    return userAgent;
  }

  HttpBackend backend() {
    return backend;
  }

  /** The configured overall per-request timeout budget, in whole seconds. */
  long requestTimeoutSeconds() {
    return retry.timeout.getSeconds();
  }

  /** Sends a request with auth, User-Agent and the retry policy applied. */
  ApiResponse call(
      String method, String url, byte[] body, String contentType, Duration baseTimeout) {
    return callWithHeaders(method, url, body, contentType, null, baseTimeout, false);
  }

  /**
   * Like {@link #call} but, when {@code doNotRetryTimeouts} is {@code true}, a transport timeout is
   * treated as a terminal failure rather than being retried (other transport/network errors and
   * retryable statuses are still retried).
   */
  ApiResponse call(
      String method,
      String url,
      byte[] body,
      String contentType,
      Duration baseTimeout,
      boolean doNotRetryTimeouts) {
    return callWithHeaders(method, url, body, contentType, null, baseTimeout, doNotRetryTimeouts);
  }

  /** Like {@link #call} but additionally sets the given extra headers on every attempt. */
  ApiResponse callWithHeaders(
      String method,
      String url,
      byte[] body,
      String contentType,
      Map<String, String> extraHeaders,
      Duration baseTimeout) {
    return callWithHeaders(method, url, body, contentType, extraHeaders, baseTimeout, false);
  }

  ApiResponse callWithHeaders(
      String method,
      String url,
      byte[] body,
      String contentType,
      Map<String, String> extraHeaders,
      Duration baseTimeout,
      boolean doNotRetryTimeouts) {

    Duration delay = retry.minDelayBetweenRetries;
    int maxAttempts = retry.maxRetries + 1;
    String path = extractPath(url);
    RuntimeException lastError = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      boolean retryable;
      try {
        ApiResponse resp =
            doAttempt(
                method, url, body, contentType, extraHeaders, attemptTimeout(baseTimeout, attempt));
        if (resp.statusCode < MAX_SUCCESS_STATUS) {
          return resp;
        }
        lastError = buildApiError(resp.statusCode, resp.body, attempt, method, path);
        retryable = isStatusRetryable(resp.statusCode);
      } catch (TransportException e) {
        lastError = e;
        // Network/timeout failures are retryable, unless the caller opted out of retrying timeouts.
        retryable = !(doNotRetryTimeouts && isTimeout(e));
      }

      if (!retryable || attempt == maxAttempts) {
        throw lastError;
      }

      sleep(randomizedDelay(delay));
      delay = minDuration(delay.multipliedBy(BACKOFF_FACTOR), retry.maxDelayBetweenRetries);
    }
    throw lastError; // unreachable in practice (maxAttempts >= 1), defensive
  }

  /**
   * Builds a fully-prepared {@link HttpRequest} with auth, User-Agent, timeout and extra headers.
   */
  HttpRequest buildRequest(
      String method,
      String url,
      byte[] body,
      String contentType,
      Map<String, String> extraHeaders,
      Duration timeout) {
    HttpRequest.BodyPublisher publisher =
        body != null
            ? HttpRequest.BodyPublishers.ofByteArray(body)
            : HttpRequest.BodyPublishers.noBody();
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create(url)).method(method, publisher).timeout(timeout);
    b.header("User-Agent", userAgent);
    if (token != null && !token.isEmpty()) {
      b.header("Authorization", "Bearer " + token);
    }
    if (contentType != null && !contentType.isEmpty()) {
      b.header("Content-Type", contentType);
    }
    if (extraHeaders != null) {
      extraHeaders.forEach(b::header);
    }
    return b.build();
  }

  private ApiResponse doAttempt(
      String method,
      String url,
      byte[] body,
      String contentType,
      Map<String, String> extraHeaders,
      Duration timeout) {
    HttpRequest request = buildRequest(method, url, body, contentType, extraHeaders, timeout);
    try {
      HttpResponse<byte[]> resp = backend.send(request);
      return new ApiResponse(resp.statusCode(), resp.headers(), resp.body());
    } catch (IOException e) {
      throw new TransportException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TransportException(e);
    }
  }

  /**
   * Returns {@code min(overall, base * 2^(attempt-1))}. The first attempt uses the per-endpoint
   * base timeout; each retry doubles it (so a slow-but-progressing connection gets more time) while
   * never exceeding the overall budget.
   */
  private Duration attemptTimeout(Duration base, int attempt) {
    Duration scaled = base;
    for (int i = 1; i < attempt; i++) {
      scaled = scaled.multipliedBy(2);
      if (scaled.compareTo(retry.timeout) >= 0) {
        return retry.timeout;
      }
    }
    return minDuration(scaled, retry.timeout);
  }

  private static boolean isStatusRetryable(int status) {
    return status == RATE_LIMIT_EXCEEDED || status >= MIN_SERVER_ERROR;
  }

  /** Reports whether a transport failure was caused by a request timeout. */
  private static boolean isTimeout(TransportException e) {
    return e.getCause() instanceof java.net.http.HttpTimeoutException;
  }

  /**
   * Returns a delay chosen randomly from {@code [delay, 2*delay)} (exponential backoff + jitter).
   */
  private static Duration randomizedDelay(Duration delay) {
    long millis = delay.toMillis();
    if (millis <= 0) {
      return delay;
    }
    return Duration.ofMillis(millis + ThreadLocalRandom.current().nextLong(millis));
  }

  private static void sleep(Duration d) {
    try {
      Thread.sleep(Math.max(0, d.toMillis()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TransportException(e);
    }
  }

  private static Duration minDuration(Duration a, Duration b) {
    return a.compareTo(b) < 0 ? a : b;
  }

  /** Builds an {@link ApifyApiException} from an API error response body. */
  static ApifyApiException buildApiError(
      int status, byte[] body, int attempt, String method, String path) {
    String type = null;
    String message = null;
    Map<String, Object> data = null;
    try {
      JsonNode root = Json.MAPPER.readTree(body);
      JsonNode error = root.get("error");
      if (error != null && error.hasNonNull("message")) {
        type = error.path("type").asText(null);
        message = error.path("message").asText(null);
        JsonNode dataNode = error.get("data");
        if (dataNode != null && dataNode.isObject()) {
          data =
              Json.MAPPER.convertValue(
                  dataNode,
                  new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        }
      }
    } catch (IOException ignored) {
      // Fall through to the generic message below.
    }
    if (message == null) {
      message =
          (body == null || body.length == 0)
              ? "unexpected error with status " + status
              : "unexpected error: " + new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }
    return new ApifyApiException(status, type, message, attempt, method, path, data);
  }

  /** Returns the path+query portion of a URL, for error reporting. */
  static String extractPath(String url) {
    String rest = url;
    int scheme = rest.indexOf("://");
    if (scheme >= 0) {
      rest = rest.substring(scheme + 3);
    }
    int slash = rest.indexOf('/');
    return slash >= 0 ? rest.substring(slash) : "";
  }

  /** Internal marker for transport-level (network/timeout) failures, which are retryable. */
  static final class TransportException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    TransportException(Throwable cause) {
      super(cause);
    }
  }

  /** Opens a live streaming response (single attempt, no retry). Used by log streaming. */
  HttpResponse<InputStream> stream(String url) {
    HttpRequest request = buildRequest("GET", url, null, null, null, retry.timeout);
    try {
      return backend.sendStreaming(request);
    } catch (IOException e) {
      throw new TransportException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TransportException(e);
    }
  }
}
