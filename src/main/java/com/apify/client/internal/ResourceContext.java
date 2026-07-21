package com.apify.client.internal;

import com.apify.client.PaginationList;
import com.apify.client.http.ApiResponse;
import com.apify.client.http.ApifyApiException;
import com.apify.client.http.ApifyTransportException;
import com.fasterxml.jackson.databind.JavaType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The resolved context for a resource client: its base URL and the shared HTTP client. The methods
 * here implement the CRUD primitives once, so each resource client stays small and consistent
 * (DRY). Internal to the client.
 */
public final class ResourceContext {

  public static final String CONTENT_TYPE_JSON = "application/json";
  public static final String CONTENT_TYPE_JSON_CHARSET = "application/json; charset=utf-8";

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

  /** API error type for a resource that does not exist. */
  private static final String ERROR_TYPE_RECORD_NOT_FOUND = "record-not-found";

  /**
   * API error type for a resource that does not exist, or exists but the caller's token cannot see
   * it (the API deliberately does not distinguish the two, to avoid leaking existence to an
   * unauthorized caller).
   */
  private static final String ERROR_TYPE_RECORD_OR_TOKEN_NOT_FOUND = "record-or-token-not-found";

  public final HttpClientCore http;

  /** Fully-qualified base URL of the resource, e.g. {@code https://api.apify.com/v2/actors/ID}. */
  final String url;

  public final QueryParams baseParams;

  /** Origin (scheme + host) the API is reached through. */
  final String apiOrigin;

  /** Origin used to build public, shareable URLs (defaults to {@link #apiOrigin}). */
  final String publicOrigin;

  /**
   * Immutable: every field is set once here. {@link #withPublicOrigin} and {@link #seedParams}
   * return a new instance rather than mutating this one, so a {@code ResourceContext} (and the
   * resource client that holds one) is safe to share across threads once built.
   */
  private ResourceContext(
      HttpClientCore http,
      String url,
      QueryParams baseParams,
      String apiOrigin,
      String publicOrigin) {
    this.http = http;
    this.url = url;
    this.baseParams = baseParams;
    this.apiOrigin = apiOrigin;
    this.publicOrigin = publicOrigin;
  }

  private ResourceContext(HttpClientCore http, String url, String baseUrl) {
    this(http, url, new QueryParams(), originOf(baseUrl), originOf(baseUrl));
  }

  /** Creates a context for a collection endpoint: {@code {base}/{resourcePath}}. */
  public static ResourceContext collection(
      HttpClientCore http, String baseUrl, String resourcePath) {
    return new ResourceContext(http, baseUrl + "/" + resourcePath, baseUrl);
  }

  /**
   * {@link #collection} plus {@link #seedParams}, in one call. Every resource client's {@code
   * nested(...)} factory (dataset/key-value-store/request-queue/log, each reached at a run's or
   * task's {@code .../{resourcePath}} path with no ID of its own) builds its context this same way
   * - sharing it here keeps that one line in exactly one place instead of duplicated four times.
   */
  public static ResourceContext nestedCollection(
      HttpClientCore http, String baseUrl, String resourcePath, QueryParams inherited) {
    return collection(http, baseUrl, resourcePath).seedParams(inherited);
  }

  /** Creates a context for a single resource: {@code {base}/{resourcePath}/{safeId}}. */
  public static ResourceContext single(
      HttpClientCore http, String baseUrl, String resourcePath, String id) {
    return new ResourceContext(http, baseUrl + "/" + resourcePath + "/" + toSafeId(id), baseUrl);
  }

  /** A copy of this context with the origin used to build public URLs overridden. */
  public ResourceContext withPublicOrigin(String publicBaseUrl) {
    return new ResourceContext(http, url, baseParams, apiOrigin, originOf(publicBaseUrl));
  }

  /** This resource's URL with an optional extra path segment appended. */
  public String subUrl(String subPath) {
    return (subPath == null || subPath.isEmpty()) ? url : url + "/" + subPath;
  }

  /**
   * The public (shareable) form of this resource's URL, swapping the API origin for the public one.
   */
  public String publicUrl(String subPath) {
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
  public QueryParams mergedParams(QueryParams params) {
    return baseParams.copy().extend(params);
  }

  /**
   * A copy of this context with {@code inherited} merged into its base params (e.g. so a last-run
   * client's pinned {@code status}/{@code origin} filters carry into its nested storage/log
   * accessors). Returns {@code this} unchanged when {@code inherited} is null or empty, so ordinary
   * nested clients are unaffected.
   */
  public ResourceContext seedParams(QueryParams inherited) {
    if (inherited == null || inherited.isEmpty()) {
      return this;
    }
    return new ResourceContext(
        http, url, baseParams.copy().extend(inherited), apiOrigin, publicOrigin);
  }

  // ---- CRUD primitives ------------------------------------------------------

  public <T> Optional<T> getResource(String subPath, QueryParams params, JavaType dataType) {
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

  public <T> Optional<T> getResource(String subPath, QueryParams params, Class<T> dataClass) {
    return getResource(subPath, params, Json.type(dataClass));
  }

  public <T> T getResourceRequired(String subPath, QueryParams params, JavaType dataType) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    ApiResponse resp = http.call("GET", u, null, "", http.baseRequestTimeout());
    return Json.parseData(resp.body, dataType);
  }

  public <T> T getResourceRequired(String subPath, QueryParams params, Class<T> dataClass) {
    return getResourceRequired(subPath, params, Json.type(dataClass));
  }

  public <T> T updateResource(String subPath, Object body, Class<T> dataClass) {
    String u = mergedParams(new QueryParams()).applyToUrl(subUrl(subPath));
    ApiResponse resp =
        http.call("PUT", u, Json.toBytes(body), CONTENT_TYPE_JSON, http.baseRequestTimeout());
    return Json.parseData(resp.body, dataClass);
  }

  /** Performs a DELETE; a not-found is treated as a successful no-op. */
  public void deleteResource(String subPath) {
    String u = mergedParams(new QueryParams()).applyToUrl(subUrl(subPath));
    try {
      http.call("DELETE", u, null, "", http.baseRequestTimeout());
    } catch (ApifyApiException e) {
      if (!isNotFound(e)) {
        throw e;
      }
    }
  }

  public <T> PaginationList<T> listResource(
      String subPath, QueryParams params, Class<T> itemClass) {
    JavaType listType = Json.parametric(PaginationList.class, Json.type(itemClass));
    return getResourceRequired(subPath, params, listType);
  }

  /**
   * Builds a lazy iterator over an offset/limit-paginated endpoint. {@code applyFilters} adds the
   * per-endpoint filter params (everything except {@code offset}/{@code limit}, which the iterator
   * drives per page). {@code totalLimit} caps the total items yielded; {@code chunkSize} is the
   * page size (both {@code null} meaning "unbounded" / "server default").
   */
  public <T> Iterator<T> iterateResource(
      String subPath,
      Long totalLimit,
      Long chunkSize,
      Long startOffset,
      Consumer<QueryParams> applyFilters,
      Class<T> itemClass) {
    // Snapshot the caller's filters once, so mutating the options object mid-iteration cannot leak
    // into later pages (the iterator owns an independent copy of every filter, offset and limit).
    QueryParams filters = new QueryParams();
    applyFilters.accept(filters);
    return new PaginatedIterator<>(
        totalLimit,
        chunkSize,
        startOffset,
        (offset, pageLimit) -> {
          QueryParams p = new QueryParams().addLong("offset", offset).addLong("limit", pageLimit);
          p.extend(filters);
          return listResource(subPath, p, itemClass);
        });
  }

  public <T> T createResource(QueryParams params, Object body, Class<T> dataClass) {
    String u = mergedParams(params).applyToUrl(subUrl(""));
    ApiResponse resp =
        http.call("POST", u, Json.toBytes(body), CONTENT_TYPE_JSON, http.baseRequestTimeout());
    return Json.parseData(resp.body, dataClass);
  }

  /** POST that gets-or-creates a named resource ({@code POST {collection}?name=...}). */
  public <T> T getOrCreateNamed(String name, Class<T> dataClass) {
    return getOrCreateNamed(name, null, dataClass);
  }

  /**
   * As {@link #getOrCreateNamed(String, Object, Class)}, wrapping a nullable {@code schema} value
   * into the {@code {"schema": ...}} request body shape every storage collection's {@code
   * getOrCreate(name, schema)} sends (a {@code null} schema sends no body, same as {@link
   * #getOrCreateNamed(String, Class)}). Shared by {@code DatasetCollectionClient}/{@code
   * KeyValueStoreCollectionClient} so neither duplicates the wrapping (DRY).
   */
  public <T> T getOrCreateNamedWithSchema(String name, Object schema, Class<T> dataClass) {
    Object body = schema == null ? null : Map.of("schema", schema);
    return getOrCreateNamed(name, body, dataClass);
  }

  /**
   * POST that gets-or-creates a named resource, optionally sending a JSON request body (e.g. a
   * storage {@code schema}). A {@code null} body sends no body, matching the plain get-or-create.
   */
  public <T> T getOrCreateNamed(String name, Object body, Class<T> dataClass) {
    QueryParams params = new QueryParams();
    if (name != null && !name.isEmpty()) {
      params.addString("name", name);
    }
    String u = params.applyToUrl(subUrl(""));
    byte[] bodyBytes = body == null ? null : Json.toBytes(body);
    ApiResponse resp =
        http.call(
            "POST", u, bodyBytes, body == null ? "" : CONTENT_TYPE_JSON, http.baseRequestTimeout());
    return Json.parseData(resp.body, dataClass);
  }

  /** POST with a raw body (optional) and content type, unwrapping the data envelope. */
  public <T> T postWithBody(
      String subPath, QueryParams params, byte[] body, String contentType, Class<T> dataClass) {
    return postWithBody(subPath, params, body, contentType, Json.type(dataClass));
  }

  public <T> T postWithBody(
      String subPath, QueryParams params, byte[] body, String contentType, JavaType dataType) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    ApiResponse resp = http.call("POST", u, body, contentType, http.baseRequestTimeout());
    return Json.parseData(resp.body, dataType);
  }

  /**
   * POST with a raw body, parsing the response body directly <em>without</em> unwrapping a {@code
   * {"data": ...}} envelope. Used by endpoints (e.g. actor input validation) whose response is a
   * plain object rather than the standard data envelope.
   */
  public <T> T postWithBodyNoEnvelope(
      String subPath, QueryParams params, byte[] body, String contentType, Class<T> dataClass) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    ApiResponse resp = http.call("POST", u, body, contentType, http.baseRequestTimeout());
    return Json.parse(resp.body, dataClass);
  }

  /** PUT with a raw body (optional) and content type, unwrapping the data envelope. */
  public <T> T putWithBody(
      String subPath, QueryParams params, byte[] body, String contentType, Class<T> dataClass) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    ApiResponse resp = http.call("PUT", u, body, contentType, http.baseRequestTimeout());
    return Json.parseData(resp.body, dataClass);
  }

  /**
   * PUT with a raw body and content type, parsing the response body directly <em>without</em>
   * unwrapping a {@code {"data": ...}} envelope. Used by endpoints (e.g. actor task input) whose
   * response is a plain object rather than the standard data envelope.
   */
  public <T> T putWithBodyNoEnvelope(
      String subPath, QueryParams params, byte[] body, String contentType, Class<T> dataClass) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    ApiResponse resp = http.call("PUT", u, body, contentType, http.baseRequestTimeout());
    return Json.parse(resp.body, dataClass);
  }

  /** DELETE with a JSON body (used for batch request deletion), unwrapping the data envelope. */
  public <T> T deleteWithBody(String subPath, QueryParams params, Object body, Class<T> dataClass) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    ApiResponse resp =
        http.call("DELETE", u, Json.toBytes(body), CONTENT_TYPE_JSON, http.baseRequestTimeout());
    return Json.parseData(resp.body, dataClass);
  }

  /** GET returning the raw response (no data envelope). Returns {@code null} on not-found. */
  public ApiResponse getRaw(String subPath, QueryParams params) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    try {
      return http.call("GET", u, null, "", http.baseRequestTimeout());
    } catch (ApifyApiException e) {
      if (isNotFound(e)) {
        return null;
      }
      throw e;
    }
  }

  /** HEAD request; returns whether the resource exists. */
  public boolean headExists(String subPath, QueryParams params) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    try {
      http.call("HEAD", u, null, "", http.baseRequestTimeout());
      return true;
    } catch (ApifyApiException e) {
      if (isNotFound(e)) {
        return false;
      }
      throw e;
    }
  }

  /** PUT with raw bytes and a content type (used for key-value-store record uploads). */
  public void putRaw(String subPath, QueryParams params, byte[] body, String contentType) {
    putRaw(subPath, params, body, contentType, http.baseRequestTimeout(), false);
  }

  /**
   * PUT with raw bytes and a content type, with an explicit per-request {@code timeout} and control
   * over whether transport timeouts are retried. Used by key-value-store record uploads that expose
   * the reference client's {@code timeoutSecs}/{@code doNotRetryTimeouts} write options.
   */
  public void putRaw(
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
  public Long clampServerWait(Long waitForFinishSecs) {
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
  public <T> T waitForFinish(
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
      throw new ApifyTransportException(e);
    }
  }

  // ---- URL / id helpers -----------------------------------------------------

  /** Reports whether an exception represents a "resource not found" API error. */
  public static boolean isNotFound(ApifyApiException e) {
    if (e.getStatusCode() != NOT_FOUND) {
      return false;
    }
    String type = e.getType();
    return ERROR_TYPE_RECORD_NOT_FOUND.equals(type)
        || ERROR_TYPE_RECORD_OR_TOKEN_NOT_FOUND.equals(type)
        || "HEAD".equals(e.getHttpMethod());
  }

  /**
   * Encodes a resource id so it is safe to embed in a URL path. Apify uses the {@code
   * username~resourcename} form, so the first {@code /} of an id is replaced with {@code ~}.
   *
   * <p>Public (not just intra-package) because {@code RunClient#metamorph} in the {@code .run}
   * package needs to apply the same normalization to a resource id supplied as an argument value
   * (as opposed to the id embedded in this client's own URL path, which {@link #single} already
   * normalizes internally) — a cross-package caller of internal plumbing, same pattern as every
   * other member here promoted narrowly for a specific caller (see the package-split rationale in
   * this class's other narrowly-public members).
   */
  public static String toSafeId(String id) {
    int slash = id.indexOf('/');
    return slash < 0 ? id : id.substring(0, slash) + "~" + id.substring(slash + 1);
  }

  /**
   * Percent-encodes a single URL path segment, so that values interpolated into the path (record
   * keys, request IDs) cannot break out of the segment.
   */
  public static String encodePathSegment(String input) {
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
