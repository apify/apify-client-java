package com.apify.client.internal;

import com.apify.client.http.ApiResponse;
import com.apify.client.http.ApifyApiException;
import com.apify.client.http.ApifyTransportException;
import com.apify.client.http.BrotliHttpCompressor;
import com.apify.client.http.ConflictError;
import com.apify.client.http.ForbiddenError;
import com.apify.client.http.GzipHttpCompressor;
import com.apify.client.http.HttpCompressor;
import com.apify.client.http.HttpTransport;
import com.apify.client.http.InvalidRequestError;
import com.apify.client.http.NotFoundError;
import com.apify.client.http.RateLimitError;
import com.apify.client.http.RetryConfig;
import com.apify.client.http.ServerError;
import com.apify.client.http.Timeout;
import com.apify.client.http.UnauthorizedError;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The orchestrating HTTP client shared by every resource client. It owns the transport, the
 * optional API token, the {@code User-Agent}, and the retry/timeout policy, and applies them to
 * every request. Internal to the client; safe for concurrent use.
 *
 * <p>Every call is non-blocking end to end: a request never occupies the calling thread while
 * waiting on the network, a retryable failure's backoff delay is a scheduled timer (see {@link
 * Async}) rather than {@code Thread.sleep}, and each retry attempt is chained via {@link
 * CompletableFuture#thenCompose} rather than looped over synchronously.
 */
public final class HttpClientCore {

  /** Status returned when the per-resource rate limit is hit. */
  private static final int RATE_LIMIT_EXCEEDED = 429;

  /** Statuses at or above this value are treated as retryable internal server errors. */
  private static final int MIN_SERVER_ERROR = 500;

  /** Responses with status below this value are treated as success. */
  public static final int MAX_SUCCESS_STATUS = 300;

  /** Exponential-backoff multiplier applied to the inter-retry delay after each attempt. */
  private static final int BACKOFF_FACTOR = 2;

  /**
   * Request bodies at or above this size (in bytes) are compressed before being sent. Small bodies
   * are left uncompressed because the CPU cost outweighs the transfer saving. Matches the reference
   * JS client's 1024-byte threshold.
   */
  private static final int MIN_COMPRESS_BYTES = 1024;

  /** Prefix required before the token value in the {@code Authorization} header. */
  private static final String AUTH_BEARER_PREFIX = "Bearer ";

  /** The API holds a {@code waitForFinish} request for at most this many seconds. */
  private static final long MAX_HOLD_SECS = 60;

  private static final Logger LOG = LoggerFactory.getLogger(HttpClientCore.class);

  private final HttpTransport transport;
  private final String token;
  private final String userAgent;
  private final RetryConfig retry;
  private final HttpCompressor compressor;
  private final Map<String, String> defaultHeaders;

  /** When set, replaces the timeout every request would otherwise get. */
  private final Timeout timeoutOverride;

  /** Requested timeouts already warned about, shared by every copy made by {@link #withTimeout}. */
  private final Set<Duration> warnedCappedTimeouts;

  /**
   * @param compressor compresses large request bodies; {@code null} picks brotli where its native
   *     library is available and gzip elsewhere
   * @param defaultHeaders headers sent with every request; they replace the built-in {@code
   *     User-Agent} and {@code Authorization} headers of the same name and are replaced by a header
   *     the request sets itself
   */
  public HttpClientCore(
      HttpTransport transport,
      String token,
      String userAgent,
      RetryConfig retry,
      HttpCompressor compressor,
      Map<String, String> defaultHeaders) {
    this(
        transport,
        token,
        userAgent,
        retry,
        compressor != null ? compressor : defaultCompressor(),
        defaultHeaders == null ? Map.of() : Map.copyOf(defaultHeaders),
        null,
        ConcurrentHashMap.newKeySet());
  }

  private static HttpCompressor defaultCompressor() {
    return BrotliHttpCompressor.isAvailable()
        ? new BrotliHttpCompressor()
        : new GzipHttpCompressor();
  }

  private HttpClientCore(
      HttpTransport transport,
      String token,
      String userAgent,
      RetryConfig retry,
      HttpCompressor compressor,
      Map<String, String> defaultHeaders,
      Timeout timeoutOverride,
      Set<Duration> warnedCappedTimeouts) {
    this.transport = transport;
    this.token = token;
    this.userAgent = userAgent;
    this.retry = retry;
    this.compressor = compressor;
    this.defaultHeaders = defaultHeaders;
    this.timeoutOverride = timeoutOverride;
    this.warnedCappedTimeouts = warnedCappedTimeouts;
  }

  public String userAgent() {
    return userAgent;
  }

  /**
   * A copy of this client that sends every request with {@code timeout} instead of the tier the
   * calling method would pick. The transport, token and retry policy are shared.
   */
  public HttpClientCore withTimeout(Timeout timeout) {
    if (timeout == null) {
      throw new IllegalArgumentException("timeout must not be null");
    }
    return new HttpClientCore(
        transport,
        token,
        userAgent,
        retry,
        compressor,
        defaultHeaders,
        timeout,
        warnedCappedTimeouts);
  }

  /** Whether {@link #withTimeout} replaced the per-method timeouts of this client. */
  public boolean hasTimeoutOverride() {
    return timeoutOverride != null;
  }

  /**
   * Resolves a timeout to the duration of its first attempt: a tier to its configured duration, an
   * exact timeout to itself, and {@link Timeout#NO_TIMEOUT} to {@code null} (no timeout). A value
   * above the configured maximum is capped at it, which logs a warning once per value, since the
   * request does not get the whole time it asked for. The override set by {@link #withTimeout}
   * takes precedence over {@code requested}.
   */
  public Duration resolveTimeout(Timeout requested) {
    Timeout effective = timeoutOverride != null ? timeoutOverride : requested;
    if (effective.isNoTimeout()) {
      return null;
    }
    Duration duration =
        effective
            .duration()
            .orElseGet(
                () ->
                    switch (effective.tier().orElseThrow()) {
                      case SHORT -> retry.timeoutShort;
                      case MEDIUM -> retry.timeoutMedium;
                      case LONG -> retry.timeoutLong;
                    });
    if (duration.compareTo(retry.timeout) > 0) {
      if (warnedCappedTimeouts.add(duration)) {
        LOG.warn(
            "The requested timeout of {}s exceeds timeoutMax ({}s) and is capped at it. Raise"
                + " timeoutMax on the client builder to allow longer request timeouts.",
            duration.toMillis() / 1000.0,
            retry.timeout.toMillis() / 1000.0);
      }
      return retry.timeout;
    }
    return duration;
  }

  /**
   * The timeout for a request that asks the API to hold its response for {@code holdSecs} with
   * {@code waitForFinish}: the hold plus the round trip {@code tier} allows, so the client does not
   * abort a request the API is still holding (which, for a start or build, would retry a call that
   * creates a resource). An override set by {@link #withTimeout} is used as given.
   */
  public Timeout timeoutWithHold(Timeout tier, Long holdSecs) {
    if (timeoutOverride != null || tier.isNoTimeout() || holdSecs == null || holdSecs <= 0) {
      return tier;
    }
    Duration base = resolveTimeout(tier);
    return Timeout.of(base.plusSeconds(Math.min(holdSecs, MAX_HOLD_SECS)));
  }

  /** Sends a request with auth, User-Agent and the retry policy applied. */
  public CompletableFuture<ApiResponse> call(
      String method, String url, byte[] body, String contentType, Timeout timeout) {
    return call(method, url, body, contentType, null, timeout, false);
  }

  /**
   * Like {@link #call(String, String, byte[], String, Timeout)} but, when {@code
   * doNotRetryTimeouts} is {@code true}, a transport timeout is treated as a terminal failure
   * rather than being retried (other transport/network errors and retryable statuses are still
   * retried).
   */
  public CompletableFuture<ApiResponse> call(
      String method,
      String url,
      byte[] body,
      String contentType,
      Timeout timeout,
      boolean doNotRetryTimeouts) {
    return call(method, url, body, contentType, null, timeout, doNotRetryTimeouts);
  }

  /**
   * Like {@link #call(String, String, byte[], String, Timeout)} but additionally sets the given
   * extra headers on every attempt.
   */
  public CompletableFuture<ApiResponse> call(
      String method,
      String url,
      byte[] body,
      String contentType,
      Map<String, String> extraHeaders,
      Timeout timeout) {
    return call(method, url, body, contentType, extraHeaders, timeout, false);
  }

  /** The canonical overload every other {@code call} convenience overload delegates to. */
  public CompletableFuture<ApiResponse> call(
      String method,
      String url,
      byte[] body,
      String contentType,
      Map<String, String> extraHeaders,
      Timeout timeout,
      boolean doNotRetryTimeouts) {

    Duration baseTimeout = resolveTimeout(timeout);

    // Compress the body once, up front, so every retry reuses the same encoded payload.
    byte[] requestBody = body;
    Map<String, String> headers = extraHeaders;
    if (shouldCompress(requestBody, contentType, extraHeaders)) {
      try {
        requestBody = compressor.compress(requestBody);
      } catch (IOException | RuntimeException e) {
        // No fallback to another coding: the configured compressor is the only one that runs.
        return failedFuture(new ApifyTransportException(e));
      }
      headers = new LinkedHashMap<>(extraHeaders == null ? Map.of() : extraHeaders);
      headers.put(HttpHeaders.CONTENT_ENCODING, compressor.contentEncoding());
    }

    String path = extractPath(url);
    int maxAttempts = retry.maxRetries + 1;
    return attemptWithRetry(
        method,
        url,
        requestBody,
        contentType,
        headers,
        baseTimeout,
        doNotRetryTimeouts,
        path,
        1,
        maxAttempts,
        retry.minDelayBetweenRetries);
  }

  /**
   * Runs one attempt and, on a retryable failure that has not exhausted {@code maxAttempts},
   * schedules the next one after a backoff delay - chained via {@link
   * CompletableFuture#thenCompose} rather than a blocking loop, so no thread is ever parked waiting
   * on the network or the delay.
   */
  private CompletableFuture<ApiResponse> attemptWithRetry(
      String method,
      String url,
      byte[] body,
      String contentType,
      Map<String, String> headers,
      Duration baseTimeout,
      boolean doNotRetryTimeouts,
      String path,
      int attempt,
      int maxAttempts,
      Duration delay) {
    Duration timeout = attemptTimeout(baseTimeout, attempt);
    return attempt(
            method, url, body, contentType, headers, timeout, doNotRetryTimeouts, attempt, path)
        .thenCompose(
            outcome -> {
              if (outcome.success()) {
                return CompletableFuture.completedFuture(outcome.response());
              }
              if (!outcome.retryable() || attempt == maxAttempts) {
                return failedFuture(outcome.error());
              }
              Duration sleepFor = randomizedDelay(delay);
              Duration nextDelay =
                  minDuration(delay.multipliedBy(BACKOFF_FACTOR), retry.maxDelayBetweenRetries);
              return Async.delay(sleepFor)
                  .thenCompose(
                      unused ->
                          attemptWithRetry(
                              method,
                              url,
                              body,
                              contentType,
                              headers,
                              baseTimeout,
                              doNotRetryTimeouts,
                              path,
                              attempt + 1,
                              maxAttempts,
                              nextDelay));
            });
  }

  /** The outcome of a single attempt: either a success response, or a retryable/terminal error. */
  private record AttemptOutcome(
      ApiResponse response, boolean success, boolean retryable, RuntimeException error) {}

  /** Sends one attempt and classifies the result (success / retryable error / terminal error). */
  private CompletableFuture<AttemptOutcome> attempt(
      String method,
      String url,
      byte[] body,
      String contentType,
      Map<String, String> extraHeaders,
      Duration timeout,
      boolean doNotRetryTimeouts,
      int attemptNum,
      String path) {
    return doAttempt(method, url, body, contentType, extraHeaders, timeout)
        .handle(
            (resp, err) -> {
              if (err == null) {
                if (resp.statusCode() < MAX_SUCCESS_STATUS) {
                  return new AttemptOutcome(resp, true, false, null);
                }
                RuntimeException apiError =
                    buildApiError(resp.statusCode(), resp.body(), attemptNum, method, path);
                return new AttemptOutcome(
                    null, false, isStatusRetryable(resp.statusCode()), apiError);
              }
              Throwable cause = unwrapCompletion(err);
              ApifyTransportException transportError =
                  cause instanceof ApifyTransportException apifyTransportException
                      ? apifyTransportException
                      : new ApifyTransportException(cause);
              // Network/timeout failures are retryable, unless the caller opted out of retrying
              // timeouts.
              boolean retryable = !(doNotRetryTimeouts && transportError.isTimeout());
              return new AttemptOutcome(null, false, retryable, transportError);
            });
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
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).method(method, publisher);
    if (timeout != null) {
      b.timeout(timeout);
    }
    b.header(HttpHeaders.USER_AGENT, userAgent);
    if (token != null && !token.isEmpty()) {
      b.header(HttpHeaders.AUTHORIZATION, AUTH_BEARER_PREFIX + token);
    }
    if (contentType != null && !contentType.isEmpty()) {
      b.header(HttpHeaders.CONTENT_TYPE, contentType);
    }
    // setHeader replaces what is already set, so a default header wins over the built-in ones and
    // a header of the request itself wins over both.
    defaultHeaders.forEach(b::setHeader);
    if (extraHeaders != null) {
      extraHeaders.forEach(b::setHeader);
    }
    return b.build();
  }

  private CompletableFuture<ApiResponse> doAttempt(
      String method,
      String url,
      byte[] body,
      String contentType,
      Map<String, String> extraHeaders,
      Duration timeout) {
    HttpRequest request = buildRequest(method, url, body, contentType, extraHeaders, timeout);
    return transport
        .sendAsync(request)
        .thenApply(resp -> new ApiResponse(resp.statusCode(), resp.headers(), resp.body()));
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
    if (base == null) {
      return null; // Timeout.NO_TIMEOUT
    }
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
   * #MIN_COMPRESS_BYTES} bytes, the caller must not have already set a {@code Content-Encoding}
   * header (which would mean the body is pre-encoded), and {@code contentType} must not already
   * carry its own compression (see {@link #isCompressibleContentType}).
   */
  private static boolean shouldCompress(
      byte[] body, String contentType, Map<String, String> extraHeaders) {
    if (body == null || body.length < MIN_COMPRESS_BYTES) {
      return false;
    }
    if (extraHeaders != null) {
      for (String key : extraHeaders.keySet()) {
        if (HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(key)) {
          return false;
        }
      }
    }
    return isCompressibleContentType(contentType);
  }

  /** Media type prefixes whose payloads already carry their own compression. */
  private static final List<String> ALREADY_COMPRESSED_MEDIA_TYPE_PREFIXES =
      List.of("audio/", "image/", "video/");

  /** Exact media types whose payloads already carry their own compression. */
  private static final Set<String> ALREADY_COMPRESSED_MEDIA_TYPES =
      Set.of(
          "application/epub+zip",
          "application/gzip",
          "application/java-archive",
          "application/vnd.android.package-archive",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/vnd.rar",
          "application/x-7z-compressed",
          "application/x-bzip",
          "application/x-bzip2",
          "application/x-gzip",
          "application/x-rar-compressed",
          "application/x-xz",
          "application/x-zip-compressed",
          "application/zip",
          "application/zstd",
          "font/woff",
          "font/woff2");

  /** Uncompressed media types that sit under an already-compressed prefix but are still raw. */
  private static final Set<String> COMPRESSIBLE_MEDIA_TYPES =
      Set.of(
          "audio/aiff",
          "audio/basic",
          "audio/l16",
          "audio/l24",
          "audio/midi",
          "audio/vnd.wave",
          "audio/wav",
          "audio/wave",
          "audio/x-aiff",
          "audio/x-wav",
          "image/bmp",
          "image/tiff",
          "image/vnd.adobe.photoshop",
          "image/vnd.microsoft.icon",
          "image/x-icon",
          "image/x-ms-bmp");

  /** Structured syntax suffixes that mark a media type as text even under a compressed prefix. */
  private static final List<String> COMPRESSIBLE_MEDIA_TYPE_SUFFIXES = List.of("+json", "+xml");

  /**
   * Reports whether a request body with the given {@code Content-Type} is worth compressing.
   * Images, audio, video and archives already carry their own compression: running them through
   * brotli or gzip burns CPU, holds a second full copy of the body in memory, and usually produces
   * output slightly larger than the input. A content type that is raw despite such a media type
   * (e.g. {@code image/bmp}, {@code audio/wav}) is still compressed, as is a structured-syntax type
   * ending in {@code +json}/{@code +xml} (e.g. {@code image/svg+xml}). A missing content type is
   * assumed compressible. Matches the reference JS client's {@code isCompressibleContentType}.
   *
   * <p>Public (like {@link #compress} and {@link #brotliAvailable}) so {@code CompressionTest},
   * outside this non-exported package, can exercise the classification directly.
   */
  public static boolean isCompressibleContentType(String contentType) {
    if (contentType == null || contentType.isEmpty()) {
      return true;
    }
    // Content-Type is case-insensitive and may carry parameters (e.g. "text/plain; charset=utf-8").
    int semicolon = contentType.indexOf(';');
    String mediaType =
        (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType)
            .trim()
            .toLowerCase(Locale.ROOT);
    if (COMPRESSIBLE_MEDIA_TYPES.contains(mediaType)) {
      return true;
    }
    for (String suffix : COMPRESSIBLE_MEDIA_TYPE_SUFFIXES) {
      if (mediaType.endsWith(suffix)) {
        return true;
      }
    }
    if (ALREADY_COMPRESSED_MEDIA_TYPES.contains(mediaType)) {
      return false;
    }
    for (String prefix : ALREADY_COMPRESSED_MEDIA_TYPE_PREFIXES) {
      if (mediaType.startsWith(prefix)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isStatusRetryable(int status) {
    return status == RATE_LIMIT_EXCEEDED || status >= MIN_SERVER_ERROR;
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

  private static Duration minDuration(Duration a, Duration b) {
    return a.compareTo(b) < 0 ? a : b;
  }

  private static CompletableFuture<ApiResponse> failedFuture(RuntimeException error) {
    CompletableFuture<ApiResponse> future = new CompletableFuture<>();
    future.completeExceptionally(error);
    return future;
  }

  /**
   * Unwraps the {@link CompletionException}/{@link ExecutionException} layers {@link
   * CompletableFuture} plumbing may add around the original failure, so callers always classify
   * (and wrap) the real cause rather than the wrapper.
   */
  public static Throwable unwrapCompletion(Throwable t) {
    Throwable current = t;
    while ((current instanceof CompletionException || current instanceof ExecutionException)
        && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  /**
   * The {@code {"error": {...}}} envelope the API sends on a non-success response, mapped directly
   * by Jackson rather than navigated field-by-field as a raw {@link
   * tools.jackson.databind.JsonNode} tree.
   */
  private static final class ErrorEnvelope {
    public ErrorBody error;
  }

  private static final class ErrorBody {
    public String type;
    public String message;
    public Map<String, Object> data;
  }

  /** Builds an {@link ApifyApiException} from an API error response body. */
  public static ApifyApiException buildApiError(
      int status, byte[] body, int attempt, String method, String path) {
    ErrorEnvelope envelope = Json.tryParse(body, ErrorEnvelope.class);
    String type = null;
    String message = null;
    Map<String, Object> data = null;
    if (envelope != null && envelope.error != null) {
      type = envelope.error.type;
      message = envelope.error.message;
      data = envelope.error.data;
    }
    if (message == null) {
      message =
          (body == null || body.length == 0)
              ? "unexpected error with status " + status
              : "unexpected error: " + new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }
    return newApiException(status, type, message, attempt, method, path, data);
  }

  /**
   * Builds the {@link ApifyApiException} subclass matching {@code status}, so callers can branch
   * with {@code instanceof} on the specific error (see the class Javadoc). A status with no
   * dedicated subclass falls back to the plain base class.
   */
  private static ApifyApiException newApiException(
      int status,
      String type,
      String message,
      int attempt,
      String method,
      String path,
      Map<String, Object> data) {
    return switch (status) {
      case 400 -> new InvalidRequestError(type, message, attempt, method, path, data);
      case 401 -> new UnauthorizedError(type, message, attempt, method, path, data);
      case 403 -> new ForbiddenError(type, message, attempt, method, path, data);
      case 404 -> new NotFoundError(type, message, attempt, method, path, data);
      case 409 -> new ConflictError(type, message, attempt, method, path, data);
      case RATE_LIMIT_EXCEEDED -> new RateLimitError(type, message, attempt, method, path, data);
      default ->
          status >= MIN_SERVER_ERROR
              ? new ServerError(status, type, message, attempt, method, path, data)
              : new ApifyApiException(status, type, message, attempt, method, path, data);
    };
  }

  /** Returns the path+query portion of a URL, for error reporting. */
  public static String extractPath(String url) {
    try {
      URI uri = new URI(url);
      String path = uri.getRawPath() == null ? "" : uri.getRawPath();
      return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    } catch (URISyntaxException e) {
      // The client itself only ever builds well-formed URLs; a malformed one here would mean a
      // caller-supplied base URL is broken, which already surfaced earlier when the request was
      // sent. Fall back to an empty path rather than letting error reporting itself throw.
      return "";
    }
  }

  /**
   * Opens a live streaming response (single attempt, no retry). Used by log streaming. The returned
   * future completes exceptionally with an {@link ApifyTransportException} on a transport failure;
   * a non-2xx status is returned as a normal response for the caller (log streaming) to inspect,
   * matching {@link HttpTransport}'s contract.
   */
  public CompletableFuture<HttpResponse<InputStream>> streamAsync(String url) {
    HttpRequest request = buildRequest("GET", url, null, null, null, resolveTimeout(Timeout.LONG));
    CompletableFuture<HttpResponse<InputStream>> result = new CompletableFuture<>();
    transport
        .sendStreamingAsync(request)
        .whenComplete(
            (resp, err) -> {
              if (err == null) {
                result.complete(resp);
              } else {
                result.completeExceptionally(new ApifyTransportException(unwrapCompletion(err)));
              }
            });
    return result;
  }

  /**
   * As {@link #streamAsync(String)}, additionally inspecting the response itself: a non-2xx status
   * is drained and thrown as the matching {@link ApifyApiException} subclass (single attempt - this
   * bypasses the retrying transport, so the attempt number is always 1), while a success response
   * completes with its live body stream. Shared by every streaming resource method (log streaming,
   * dataset export streaming) so the drain-and-throw logic lives in one place.
   */
  public CompletableFuture<InputStream> streamOrThrow(String url) {
    return streamAsync(url)
        .thenApply(
            resp -> {
              if (resp.statusCode() >= MAX_SUCCESS_STATUS) {
                byte[] body = drainQuietly(resp.body());
                throw buildApiError(resp.statusCode(), body, 1, "GET", extractPath(url));
              }
              return resp.body();
            });
  }

  /**
   * Reads a stream to the end and closes it, for capturing a non-2xx streaming response's error
   * body; a read failure is swallowed (the stream is being drained only to describe an error that
   * already happened, so a second failure here is not worth surfacing over the original one) and
   * yields an empty body instead.
   */
  private static byte[] drainQuietly(InputStream in) {
    try (InputStream stream = in) {
      return stream.readAllBytes();
    } catch (IOException e) {
      return new byte[0];
    }
  }
}
