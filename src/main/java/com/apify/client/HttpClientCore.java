package com.apify.client;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.Encoder;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPOutputStream;

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

  /**
   * Request bodies at or above this size (in bytes) are compressed before being sent. Small bodies
   * are left uncompressed because the CPU cost outweighs the transfer saving. Matches the reference
   * JS client's 1024-byte threshold.
   */
  private static final int MIN_COMPRESS_BYTES = 1024;

  /** Header announcing the request-body content coding to the server. */
  private static final String CONTENT_ENCODING_HEADER = "Content-Encoding";

  /** {@code Content-Encoding} value for brotli-compressed bodies (matches the reference client). */
  private static final String ENCODING_BROTLI = "br";

  /** {@code Content-Encoding} value for gzip-compressed bodies (the fallback coding). */
  private static final String ENCODING_GZIP = "gzip";

  /**
   * Whether a brotli native codec loaded for the running platform. Resolved once at class load:
   * brotli4j needs a platform-specific native library, so on platforms without one (or if loading
   * fails) the client falls back to gzip. Mirrors the reference JS client, which prefers brotli and
   * falls back to gzip.
   */
  private static final boolean BROTLI_AVAILABLE = detectBrotli();

  private static boolean detectBrotli() {
    try {
      Brotli4jLoader.ensureAvailability();
      return true;
    } catch (Throwable t) {
      // Native codec unavailable (no bundled binary for this OS/arch, or a link error). Catch
      // Throwable because native loading can raise UnsatisfiedLinkError/NoClassDefFoundError, not
      // just Exception. The client stays fully functional using gzip.
      return false;
    }
  }

  /** Reports whether the brotli path is active on this platform (package-private for tests). */
  static boolean brotliAvailable() {
    return BROTLI_AVAILABLE;
  }

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

  /** The configured maximum per-attempt request timeout, in whole seconds. */
  long requestTimeoutSeconds() {
    return retry.timeout.getSeconds();
  }

  /**
   * The default per-attempt request timeout used as the base for standard API calls. It is the
   * client's configured timeout (single source of truth), so a caller-configured timeout applies to
   * the first attempt too. Individual calls may pass a smaller base (e.g. key-value-store uploads),
   * which then grows back up toward this value on retries.
   */
  Duration baseRequestTimeout() {
    return retry.timeout;
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

    // Compress the body once, up front, so every retry reuses the same encoded payload.
    byte[] requestBody = body;
    Map<String, String> headers = extraHeaders;
    if (shouldCompress(requestBody, extraHeaders)) {
      Compressed compressed = compress(requestBody, BROTLI_AVAILABLE);
      requestBody = compressed.body;
      headers = new LinkedHashMap<>(extraHeaders == null ? Map.of() : extraHeaders);
      headers.put(CONTENT_ENCODING_HEADER, compressed.encoding);
    }

    Duration delay = retry.minDelayBetweenRetries;
    int maxAttempts = retry.maxRetries + 1;
    String path = extractPath(url);
    RuntimeException lastError = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      boolean retryable;
      try {
        ApiResponse resp =
            doAttempt(
                method,
                url,
                requestBody,
                contentType,
                headers,
                attemptTimeout(baseTimeout, attempt));
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
   * Returns {@code min(configured, base * 2^(attempt-1))}: the per-attempt socket timeout. The
   * first attempt uses the per-call base timeout; each retry doubles it (so a slow-but-progressing
   * connection gets more time) while never exceeding the configured maximum. This mirrors the
   * reference client's per-try timeout model ({@code Math.min(timeoutMillis, base *
   * 2**(attempt-1))}). Note this bounds each individual attempt, not the cumulative wall-clock time
   * across all retries.
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

  /**
   * Reports whether a request body should be compressed: it must be present, at least {@link
   * #MIN_COMPRESS_BYTES} bytes, and the caller must not have already set a {@code Content-Encoding}
   * header (which would mean the body is pre-encoded).
   */
  private static boolean shouldCompress(byte[] body, Map<String, String> extraHeaders) {
    if (body == null || body.length < MIN_COMPRESS_BYTES) {
      return false;
    }
    if (extraHeaders != null) {
      for (String key : extraHeaders.keySet()) {
        if (CONTENT_ENCODING_HEADER.equalsIgnoreCase(key)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * A compressed request body together with the {@code Content-Encoding} token that describes it.
   */
  static final class Compressed {
    final byte[] body;
    final String encoding;

    Compressed(byte[] body, String encoding) {
      this.body = body;
      this.encoding = encoding;
    }
  }

  /**
   * Compresses a request body, preferring brotli and falling back to gzip, matching the reference
   * JS client. When {@code preferBrotli} is {@code true} the body is brotli-encoded ({@code
   * Content-Encoding: br}); otherwise it is gzip-encoded ({@code Content-Encoding: gzip}). Callers
   * pass {@link #BROTLI_AVAILABLE}; making the coding an explicit parameter keeps this a pure
   * function of its inputs rather than of hidden static state. Package-private.
   */
  static Compressed compress(byte[] data, boolean preferBrotli) {
    return preferBrotli
        ? new Compressed(brotli(data), ENCODING_BROTLI)
        : new Compressed(gzip(data), ENCODING_GZIP);
  }

  /** Brotli-compresses a request body using the loaded native codec. */
  private static byte[] brotli(byte[] data) {
    try {
      return Encoder.compress(data);
    } catch (IOException e) {
      // Encoding an in-memory byte[] performs no real I/O, so this is unreachable in practice.
      throw new TransportException(e);
    }
  }

  /**
   * Gzip-compresses a request body. Used as the fallback coding when no brotli native codec is
   * available for the running platform; the JDK always ships a gzip codec ({@link
   * GZIPOutputStream}).
   */
  private static byte[] gzip(byte[] data) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
    try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
      gz.write(data);
    } catch (IOException e) {
      // Compressing an in-memory byte[] cannot perform real I/O, so this is unreachable in
      // practice.
      throw new TransportException(e);
    }
    return out.toByteArray();
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
