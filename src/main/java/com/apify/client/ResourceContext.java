package com.apify.client;

import com.fasterxml.jackson.databind.JavaType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The resolved context for a resource client: its base URL and the shared HTTP client. The methods
 * here implement the CRUD primitives once, so each resource client stays small and consistent
 * (DRY). Internal to the client.
 */
final class ResourceContext {

  /** Per-request timeout applied to all API calls (6 minutes). Single source of truth. */
  static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(360);

  static final String CONTENT_TYPE_JSON = "application/json";
  static final String CONTENT_TYPE_JSON_CHARSET = "application/json; charset=utf-8";

  /** How long to wait between polls while waiting for a run/build to finish. */
  private static final Duration WAIT_POLL_INTERVAL = Duration.ofMillis(250);

  /** Server-side waitForFinish chunk size (the API caps server waiting at 60 seconds). */
  private static final long WAIT_REQUEST_SECS = 60;

  /**
   * Safety margin subtracted from the configured per-request timeout when choosing the server-side
   * {@code waitForFinish} value, so the server responds before the client's socket timeout fires
   * (otherwise a short client timeout would abort every healthy poll and burn all retries).
   */
  private static final long WAIT_TIMEOUT_MARGIN_SECS = 5;

  /**
   * Finite upper bound used when the caller asks to wait "indefinitely" ({@code waitSecs == null}).
   * The API will not accept "Infinity", and an unbounded loop can spin forever on a transient 404;
   * 999999s (~11.5 days) is effectively indefinite while guaranteeing termination. Mirrors the
   * reference client's MAX_WAIT_FOR_FINISH.
   */
  private static final long MAX_WAIT_FOR_FINISH_SECS = 999999;

  private static final int NOT_FOUND = 404;

  final HttpClientCore http;

  /** Fully-qualified base URL of the resource, e.g. {@code https://api.apify.com/v2/actors/ID}. */
  final String url;

  final QueryParams baseParams;

  /** Origin (scheme + host) the API is reached through. */
  final String apiOrigin;

  /** Origin used to build public, shareable URLs (defaults to {@link #apiOrigin}). */
  String publicOrigin;

  private ResourceContext(HttpClientCore http, String url, String baseUrl) {
    this.http = http;
    this.url = url;
    this.baseParams = new QueryParams();
    this.apiOrigin = originOf(baseUrl);
    this.publicOrigin = this.apiOrigin;
  }

  /** Creates a context for a collection endpoint: {@code {base}/{resourcePath}}. */
  static ResourceContext collection(HttpClientCore http, String baseUrl, String resourcePath) {
    return new ResourceContext(http, baseUrl + "/" + resourcePath, baseUrl);
  }

  /** Creates a context for a single resource: {@code {base}/{resourcePath}/{safeId}}. */
  static ResourceContext single(
      HttpClientCore http, String baseUrl, String resourcePath, String id) {
    return new ResourceContext(http, baseUrl + "/" + resourcePath + "/" + toSafeId(id), baseUrl);
  }

  /** Overrides the origin used when building public URLs. */
  ResourceContext withPublicOrigin(String publicBaseUrl) {
    this.publicOrigin = originOf(publicBaseUrl);
    return this;
  }

  /** This resource's URL with an optional extra path segment appended. */
  String subUrl(String subPath) {
    return (subPath == null || subPath.isEmpty()) ? url : url + "/" + subPath;
  }

  /**
   * The public (shareable) form of this resource's URL, swapping the API origin for the public one.
   */
  String publicUrl(String subPath) {
    String apiUrl = subUrl(subPath);
    if (publicOrigin.equals(apiOrigin)) {
      return apiUrl;
    }
    if (apiUrl.startsWith(apiOrigin)) {
      return publicOrigin + apiUrl.substring(apiOrigin.length());
    }
    return apiUrl;
  }

  /** Merges the inherited base params with per-call params. */
  QueryParams mergedParams(QueryParams params) {
    return baseParams.copy().extend(params);
  }

  // ---- CRUD primitives ------------------------------------------------------

  <T> Optional<T> getResource(String subPath, QueryParams params, JavaType dataType) {
    try {
      // ofNullable, not of: an HTTP 200 with body {"data": null} unwraps to null, which is a valid
      // "no resource" answer rather than a programming error — never surface it as a raw NPE.
      return Optional.ofNullable(getResourceRequired(subPath, params, dataType));
    } catch (ApifyApiException e) {
      if (isNotFound(e)) {
        return Optional.empty();
      }
      throw e;
    }
  }

  <T> Optional<T> getResource(String subPath, QueryParams params, Class<T> dataClass) {
    return getResource(subPath, params, Json.type(dataClass));
  }

  <T> T getResourceRequired(String subPath, QueryParams params, JavaType dataType) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    ApiResponse resp = http.call("GET", u, null, "", DEFAULT_REQUEST_TIMEOUT);
    return Json.parseData(resp.body, dataType);
  }

  <T> T getResourceRequired(String subPath, QueryParams params, Class<T> dataClass) {
    return getResourceRequired(subPath, params, Json.type(dataClass));
  }

  <T> T updateResource(String subPath, Object body, Class<T> dataClass) {
    String u = mergedParams(new QueryParams()).applyToUrl(subUrl(subPath));
    ApiResponse resp =
        http.call("PUT", u, Json.toBytes(body), CONTENT_TYPE_JSON, DEFAULT_REQUEST_TIMEOUT);
    return Json.parseData(resp.body, dataClass);
  }

  /** Performs a DELETE; a not-found is treated as a successful no-op. */
  void deleteResource(String subPath) {
    String u = mergedParams(new QueryParams()).applyToUrl(subUrl(subPath));
    try {
      http.call("DELETE", u, null, "", DEFAULT_REQUEST_TIMEOUT);
    } catch (ApifyApiException e) {
      if (!isNotFound(e)) {
        throw e;
      }
    }
  }

  <T> PaginationList<T> listResource(String subPath, QueryParams params, Class<T> itemClass) {
    JavaType listType = Json.parametric(PaginationList.class, Json.type(itemClass));
    return getResourceRequired(subPath, params, listType);
  }

  <T> T createResource(QueryParams params, Object body, Class<T> dataClass) {
    String u = mergedParams(params).applyToUrl(subUrl(""));
    ApiResponse resp =
        http.call("POST", u, Json.toBytes(body), CONTENT_TYPE_JSON, DEFAULT_REQUEST_TIMEOUT);
    return Json.parseData(resp.body, dataClass);
  }

  /** POST that gets-or-creates a named resource ({@code POST {collection}?name=...}). */
  <T> T getOrCreateNamed(String name, Class<T> dataClass) {
    QueryParams params = new QueryParams();
    if (name != null && !name.isEmpty()) {
      params.addString("name", name);
    }
    String u = params.applyToUrl(subUrl(""));
    ApiResponse resp = http.call("POST", u, null, "", DEFAULT_REQUEST_TIMEOUT);
    return Json.parseData(resp.body, dataClass);
  }

  /** POST with a raw body (optional) and content type, unwrapping the data envelope. */
  <T> T postWithBody(
      String subPath, QueryParams params, byte[] body, String contentType, Class<T> dataClass) {
    return postWithBody(subPath, params, body, contentType, Json.type(dataClass));
  }

  <T> T postWithBody(
      String subPath, QueryParams params, byte[] body, String contentType, JavaType dataType) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    ApiResponse resp = http.call("POST", u, body, contentType, DEFAULT_REQUEST_TIMEOUT);
    return Json.parseData(resp.body, dataType);
  }

  /** DELETE with a JSON body (used for batch request deletion), unwrapping the data envelope. */
  <T> T deleteWithBody(String subPath, QueryParams params, Object body, Class<T> dataClass) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    ApiResponse resp =
        http.call("DELETE", u, Json.toBytes(body), CONTENT_TYPE_JSON, DEFAULT_REQUEST_TIMEOUT);
    return Json.parseData(resp.body, dataClass);
  }

  /** GET returning the raw response (no data envelope). Returns {@code null} on not-found. */
  ApiResponse getRaw(String subPath, QueryParams params) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    try {
      return http.call("GET", u, null, "", DEFAULT_REQUEST_TIMEOUT);
    } catch (ApifyApiException e) {
      if (isNotFound(e)) {
        return null;
      }
      throw e;
    }
  }

  /** HEAD request; returns whether the resource exists. */
  boolean headExists(String subPath, QueryParams params) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    try {
      http.call("HEAD", u, null, "", DEFAULT_REQUEST_TIMEOUT);
      return true;
    } catch (ApifyApiException e) {
      if (isNotFound(e)) {
        return false;
      }
      throw e;
    }
  }

  /** PUT with raw bytes and a content type (used for key-value-store record uploads). */
  void putRaw(String subPath, QueryParams params, byte[] body, String contentType) {
    putRaw(subPath, params, body, contentType, DEFAULT_REQUEST_TIMEOUT, false);
  }

  /**
   * PUT with raw bytes and a content type, with an explicit per-request {@code timeout} and control
   * over whether transport timeouts are retried. Used by key-value-store record uploads that expose
   * the reference client's {@code timeoutSecs}/{@code doNotRetryTimeouts} write options.
   */
  void putRaw(
      String subPath,
      QueryParams params,
      byte[] body,
      String contentType,
      Duration timeout,
      boolean doNotRetryTimeouts) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    http.call("PUT", u, body, contentType, timeout, doNotRetryTimeouts);
  }

  /**
   * The largest server-side {@code waitForFinish} value that is safe to send: below the configured
   * per-request timeout by a safety margin (or the API's 60s cap when no finite timeout is set), so
   * the server always responds before the client's socket timeout fires.
   */
  private long serverWaitCapSecs() {
    long configuredTimeoutSecs = http.requestTimeoutSeconds();
    return configuredTimeoutSecs > 0
        ? Math.max(0, configuredTimeoutSecs - WAIT_TIMEOUT_MARGIN_SECS)
        : WAIT_REQUEST_SECS;
  }

  /**
   * Clamps a caller-supplied server-side {@code waitForFinish} value (seconds) to {@link
   * #serverWaitCapSecs()}, so a synchronous get/wait can never ask the server to hold the
   * connection longer than the client's own per-request timeout. Returns {@code null} for a {@code
   * null} input (no server-side wait requested).
   */
  Long clampServerWait(Long waitForFinishSecs) {
    if (waitForFinishSecs == null) {
      return null;
    }
    return Math.min(Math.max(0, waitForFinishSecs), serverWaitCapSecs());
  }

  /**
   * Polls a GET endpoint with {@code waitForFinish} until the resource reaches a terminal state or
   * the wait budget elapses. {@code waitSecs == null} means "wait indefinitely", implemented as a
   * finite but very large bound so the loop always terminates.
   *
   * <p>The budget is a pure time bound, evaluated independently of whether the resource is
   * currently present: a just-started run/build can transiently return 404 (database-replica lag),
   * which is treated as "not yet available".
   */
  <T> T waitForFinish(
      Long waitSecs, String resourceName, JavaType dataType, Predicate<T> isTerminal) {
    // Clamp to MAX_WAIT_FOR_FINISH_SECS so a pathological waitSecs near Long.MAX_VALUE cannot
    // overflow budgetMillis into a negative value (which would degrade the wait into a single
    // poll).
    long effectiveWaitSecs =
        waitSecs != null
            ? Math.min(Math.max(waitSecs, 0), MAX_WAIT_FOR_FINISH_SECS)
            : MAX_WAIT_FOR_FINISH_SECS;
    long budgetMillis = effectiveWaitSecs * 1000L;
    long start = System.currentTimeMillis();

    // Never ask the server to hold the connection longer than the client's own per-request timeout,
    // or a short configured timeout would abort every poll (HttpTimeoutException) and exhaust the
    // retry budget on an otherwise-healthy run. A value of 0 disables server-side waiting and falls
    // back to pure client-side polling.
    long serverWaitCap = serverWaitCapSecs();

    T resource = null;
    boolean present = false;

    while (true) {
      long elapsed = System.currentTimeMillis() - start;
      long remainingSecs = (budgetMillis - elapsed) / 1000L;
      long requestSecs =
          Math.min(Math.min(Math.max(remainingSecs, 0), WAIT_REQUEST_SECS), serverWaitCap);

      QueryParams params = new QueryParams();
      params.addLong("waitForFinish", requestSecs);

      Optional<T> res = getResource("", params, dataType);
      if (res.isPresent()) {
        resource = res.get();
        present = true;
        if (isTerminal.test(resource)) {
          return resource;
        }
      }

      if (System.currentTimeMillis() - start >= budgetMillis) {
        break;
      }
      sleep(WAIT_POLL_INTERVAL);
    }

    if (present) {
      return resource;
    }
    throw new IllegalStateException(
        "waiting for "
            + resourceName
            + " to finish failed: cannot fetch "
            + resourceName
            + " details from the server");
  }

  private static void sleep(Duration d) {
    try {
      Thread.sleep(d.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HttpClientCore.TransportException(e);
    }
  }

  // ---- URL / id helpers -----------------------------------------------------

  /** Reports whether an exception represents a "resource not found" API error. */
  static boolean isNotFound(ApifyApiException e) {
    if (e.getStatusCode() != NOT_FOUND) {
      return false;
    }
    String type = e.getType();
    return "record-not-found".equals(type)
        || "record-or-token-not-found".equals(type)
        || "HEAD".equals(e.getHttpMethod());
  }

  /**
   * Encodes a resource id so it is safe to embed in a URL path. Apify uses the {@code
   * username~resourcename} form, so the first {@code /} of an id is replaced with {@code ~}.
   */
  static String toSafeId(String id) {
    int slash = id.indexOf('/');
    return slash < 0 ? id : id.substring(0, slash) + "~" + id.substring(slash + 1);
  }

  /**
   * Percent-encodes a single URL path segment, so that values interpolated into the path (record
   * keys, request IDs) cannot break out of the segment.
   */
  static String encodePathSegment(String input) {
    return URLEncoder.encode(input, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /** Extracts the origin ({@code scheme://host[:port]}) from a URL, dropping any path. */
  static String originOf(String rawUrl) {
    String rest = rawUrl;
    String scheme = "";
    int i = rest.indexOf("://");
    if (i >= 0) {
      scheme = rest.substring(0, i + 3);
      rest = rest.substring(i + 3);
    }
    int slash = rest.indexOf('/');
    if (slash >= 0) {
      rest = rest.substring(0, slash);
    }
    return scheme + rest;
  }
}
