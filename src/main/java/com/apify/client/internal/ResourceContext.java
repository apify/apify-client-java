package com.apify.client.internal;

import com.apify.client.PaginationList;
import com.apify.client.http.ApiResponse;
import com.apify.client.http.ApifyApiException;
import com.apify.client.http.ApifyTransportException;
import com.apify.client.http.Timeout;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Predicate;
import tools.jackson.databind.JavaType;

/**
 * The resolved context for a resource client: its base URL and the shared HTTP client. The methods
 * here implement the CRUD primitives once, so each resource client stays small and consistent
 * (DRY). Internal to the client.
 *
 * <p>Every primitive is non-blocking: it returns a {@link CompletableFuture} (or, for a paginated
 * collection's lazy iteration, a {@link Flow.Publisher}) rather than blocking the calling thread on
 * the underlying HTTP exchange.
 */
public final class ResourceContext {

  public static final String CONTENT_TYPE_JSON = "application/json";
  public static final String CONTENT_TYPE_JSON_CHARSET = "application/json; charset=utf-8";

  /** How long to wait between polls while waiting for a run/build to finish. */
  private static final Duration WAIT_POLL_INTERVAL = Duration.ofMillis(250);

  /** Server-side waitForFinish chunk size (the API caps server waiting at 60 seconds). */
  private static final long WAIT_REQUEST_SECS = 60;

  /**
   * Finite upper bound used when the caller asks to wait "indefinitely" ({@code waitSecs == null}).
   * The API will not accept "Infinity", and an unbounded loop can spin forever on a transient 404;
   * 999999s (~11.5 days) is effectively indefinite while guaranteeing termination. Mirrors the
   * reference client's MAX_WAIT_FOR_FINISH.
   */
  private static final long MAX_WAIT_FOR_FINISH_SECS = 999999;

  private static final int NOT_FOUND = 404;

  /**
   * Upper bound on how long {@link #waitForFinish} keeps polling a resource that keeps answering
   * 404, before giving up and throwing that {@link ApifyApiException}. A resource that was just
   * created can 404 briefly (database-replica lag), which this bound rides out; anything longer
   * means the id most likely never existed, so polling for the rest of a long (or indefinite) wait
   * budget would only delay reporting that. Mirrors the reference client.
   */
  private static final long MAX_WAIT_FOR_MISSING_RESOURCE_MILLIS = 3000;

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
   * Whether this context addresses its resource by an explicit id of its own ({@link #single}), as
   * opposed to a nested path with no id ({@link #collection}/{@link #nestedCollection}, e.g. a
   * run's {@code .../dataset}). A 404 is unambiguous for the former (that id does not exist) but
   * not for the latter (either the parent or the sub-resource could be gone), which is why {@link
   * #getResourceUnlessAmbiguous} and {@link #deleteResourceUnlessAmbiguous} key their behavior on
   * this flag - see those methods and each affected resource client's {@code get()}/{@code
   * delete()} Javadoc.
   */
  private final boolean ownId;

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
      String publicOrigin,
      boolean ownId) {
    this.http = http;
    this.url = url;
    this.baseParams = baseParams;
    this.apiOrigin = apiOrigin;
    this.publicOrigin = publicOrigin;
    this.ownId = ownId;
  }

  private ResourceContext(HttpClientCore http, String url, String baseUrl, boolean ownId) {
    this(http, url, new QueryParams(), originOf(baseUrl), originOf(baseUrl), ownId);
  }

  /** Creates a context for a collection endpoint: {@code {base}/{resourcePath}}. */
  public static ResourceContext collection(
      HttpClientCore http, String baseUrl, String resourcePath) {
    return new ResourceContext(http, baseUrl + "/" + resourcePath, baseUrl, false);
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
    return new ResourceContext(
        http, baseUrl + "/" + resourcePath + "/" + encodePathSegment(toSafeId(id)), baseUrl, true);
  }

  /** A copy of this context with the origin used to build public URLs overridden. */
  public ResourceContext withPublicOrigin(String publicBaseUrl) {
    return new ResourceContext(http, url, baseParams, apiOrigin, originOf(publicBaseUrl), ownId);
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
        http, url, baseParams.copy().extend(inherited), apiOrigin, publicOrigin, ownId);
  }

  // ---- CRUD primitives ------------------------------------------------------

  public <T> CompletableFuture<Optional<T>> getResource(
      String subPath, QueryParams params, JavaType dataType) {
    return getResource(subPath, params, dataType, Timeout.SHORT);
  }

  public <T> CompletableFuture<Optional<T>> getResource(
      String subPath, QueryParams params, JavaType dataType, Timeout timeout) {
    // ofNullable, not of: an HTTP 200 with body {"data": null} unwraps to null, which is a valid
    // "no resource" answer rather than a programming error — never surface it as a raw NPE.
    // Explicit <T> witness: getResourceRequired is the *receiver* of .handle below (not an argument
    // to another generic call), so unlike a direct `return`, its type parameter cannot be inferred
    // from the surrounding target type and must be pinned here.
    return this.<T>getResourceRequired(subPath, params, dataType, timeout)
        .handle(
            (value, error) -> {
              if (error == null) {
                return Optional.ofNullable(value);
              }
              Throwable cause = HttpClientCore.unwrapCompletion(error);
              if (cause instanceof ApifyApiException apiError && isNotFound(apiError)) {
                return Optional.<T>empty();
              }
              throw asRuntimeException(cause);
            });
  }

  public <T> CompletableFuture<Optional<T>> getResource(
      String subPath, QueryParams params, Class<T> dataClass) {
    return getResource(subPath, params, Json.type(dataClass));
  }

  public <T> CompletableFuture<Optional<T>> getResource(
      String subPath, QueryParams params, Class<T> dataClass, Timeout timeout) {
    return getResource(subPath, params, Json.type(dataClass), timeout);
  }

  public <T> CompletableFuture<T> getResourceRequired(
      String subPath, QueryParams params, JavaType dataType) {
    return getResourceRequired(subPath, params, dataType, Timeout.SHORT);
  }

  public <T> CompletableFuture<T> getResourceRequired(
      String subPath, QueryParams params, JavaType dataType, Timeout timeout) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    return http.call("GET", u, null, "", timeout)
        .thenApply(resp -> Json.<T>parseData(resp.body(), dataType));
  }

  public <T> CompletableFuture<T> getResourceRequired(
      String subPath, QueryParams params, Class<T> dataClass) {
    return getResourceRequired(subPath, params, Json.type(dataClass));
  }

  public <T> CompletableFuture<T> getResourceRequired(
      String subPath, QueryParams params, Class<T> dataClass, Timeout timeout) {
    return getResourceRequired(subPath, params, Json.type(dataClass), timeout);
  }

  /**
   * Like {@link #getResource}, but a 404 is only swallowed to {@link Optional#empty()} when this
   * context {@link #ownId addresses its resource by an explicit id}. Otherwise (a nested path with
   * no id of its own, e.g. a run's default dataset) the 404 is ambiguous - it may mean the parent
   * is gone rather than the sub-resource - so it is rethrown as an {@link ApifyApiException}
   * ({@link com.apify.client.http.NotFoundError}) instead of being hidden behind an empty result.
   * Used by every storage/log resource client's {@code get()}.
   */
  public <T> CompletableFuture<Optional<T>> getResourceUnlessAmbiguous(
      String subPath, QueryParams params, JavaType dataType) {
    return getResourceUnlessAmbiguous(subPath, params, dataType, Timeout.SHORT);
  }

  public <T> CompletableFuture<Optional<T>> getResourceUnlessAmbiguous(
      String subPath, QueryParams params, JavaType dataType, Timeout timeout) {
    if (ownId) {
      return getResource(subPath, params, dataType, timeout);
    }
    return this.<T>getResourceRequired(subPath, params, dataType, timeout)
        .thenApply(Optional::ofNullable);
  }

  public <T> CompletableFuture<Optional<T>> getResourceUnlessAmbiguous(
      String subPath, QueryParams params, Class<T> dataClass) {
    return getResourceUnlessAmbiguous(subPath, params, Json.type(dataClass));
  }

  public <T> CompletableFuture<T> updateResource(String subPath, Object body, Class<T> dataClass) {
    return updateResource(subPath, body, dataClass, Timeout.SHORT);
  }

  public <T> CompletableFuture<T> updateResource(
      String subPath, Object body, Class<T> dataClass, Timeout timeout) {
    String u = mergedParams(new QueryParams()).applyToUrl(subUrl(subPath));
    return http.call("PUT", u, Json.toBytes(body), CONTENT_TYPE_JSON, timeout)
        .thenApply(resp -> Json.parseData(resp.body(), dataClass));
  }

  /** Performs a DELETE; a not-found is treated as a successful no-op. */
  public CompletableFuture<Void> deleteResource(String subPath) {
    String u = mergedParams(new QueryParams()).applyToUrl(subUrl(subPath));
    return http.call("DELETE", u, null, "", Timeout.SHORT)
        .handle(
            (resp, error) -> {
              if (error == null) {
                return null;
              }
              Throwable cause = HttpClientCore.unwrapCompletion(error);
              if (cause instanceof ApifyApiException apiError && isNotFound(apiError)) {
                return null;
              }
              throw asRuntimeException(cause);
            });
  }

  /**
   * Like {@link #deleteResource}, but - mirroring {@link #getResourceUnlessAmbiguous} - a 404 is
   * only treated as a successful no-op when this context addresses its resource by an explicit id.
   * Otherwise it is rethrown, since it may mean the parent (not the sub-resource) is gone.
   */
  public CompletableFuture<Void> deleteResourceUnlessAmbiguous(String subPath) {
    if (ownId) {
      return deleteResource(subPath);
    }
    String u = mergedParams(new QueryParams()).applyToUrl(subUrl(subPath));
    return http.call("DELETE", u, null, "", Timeout.SHORT).thenApply(resp -> null);
  }

  public <T> CompletableFuture<PaginationList<T>> listResource(
      String subPath, QueryParams params, Class<T> itemClass) {
    return listResource(subPath, params, itemClass, Timeout.MEDIUM);
  }

  public <T> CompletableFuture<PaginationList<T>> listResource(
      String subPath, QueryParams params, Class<T> itemClass, Timeout timeout) {
    JavaType listType = Json.parametric(PaginationList.class, Json.type(itemClass));
    return getResourceRequired(subPath, params, listType, timeout);
  }

  /**
   * Builds a lazy, backpressure-aware {@link Flow.Publisher} over an offset/limit-paginated
   * endpoint. {@code applyFilters} adds the per-endpoint filter params (everything except {@code
   * offset}/{@code limit}, which the publisher drives per page). {@code totalLimit} caps the total
   * items yielded; {@code chunkSize} is the page size (both {@code null} meaning "unbounded" /
   * "server default").
   */
  public <T> Flow.Publisher<T> iterateResource(
      String subPath,
      Long totalLimit,
      Long chunkSize,
      Long startOffset,
      Consumer<QueryParams> applyFilters,
      Class<T> itemClass) {
    // Snapshot the caller's filters once, so mutating the options object mid-iteration cannot leak
    // into later pages (the publisher owns an independent copy of every filter, offset and limit).
    QueryParams filters = new QueryParams();
    applyFilters.accept(filters);
    return new AsyncPaginatedPublisher<>(
        totalLimit,
        chunkSize,
        startOffset,
        (offset, pageLimit) -> {
          QueryParams p = new QueryParams().addLong("offset", offset).addLong("limit", pageLimit);
          p.extend(filters);
          return listResource(subPath, p, itemClass);
        });
  }

  public <T> CompletableFuture<T> createResource(
      QueryParams params, Object body, Class<T> dataClass) {
    return createResource(params, body, dataClass, Timeout.SHORT);
  }

  public <T> CompletableFuture<T> createResource(
      QueryParams params, Object body, Class<T> dataClass, Timeout timeout) {
    String u = mergedParams(params).applyToUrl(subUrl(""));
    return http.call("POST", u, Json.toBytes(body), CONTENT_TYPE_JSON, timeout)
        .thenApply(resp -> Json.parseData(resp.body(), dataClass));
  }

  /** POST that gets-or-creates a named resource ({@code POST {collection}?name=...}). */
  public <T> CompletableFuture<T> getOrCreateNamed(String name, Class<T> dataClass) {
    return getOrCreateNamed(name, null, dataClass);
  }

  /**
   * As {@link #getOrCreateNamed(String, Object, Class)}, wrapping a nullable {@code schema} value
   * into the {@code {"schema": ...}} request body shape every storage collection's {@code
   * getOrCreate(name, schema)} sends (a {@code null} schema sends no body, same as {@link
   * #getOrCreateNamed(String, Class)}). Shared by {@code DatasetCollectionClient}/{@code
   * KeyValueStoreCollectionClient} so neither duplicates the wrapping (DRY).
   */
  public <T> CompletableFuture<T> getOrCreateNamedWithSchema(
      String name, Object schema, Class<T> dataClass) {
    Object body = schema == null ? null : Map.of("schema", schema);
    return getOrCreateNamed(name, body, dataClass);
  }

  /**
   * POST that gets-or-creates a named resource, optionally sending a JSON request body (e.g. a
   * storage {@code schema}). A {@code null} body sends no body, matching the plain get-or-create.
   */
  public <T> CompletableFuture<T> getOrCreateNamed(String name, Object body, Class<T> dataClass) {
    QueryParams params = new QueryParams();
    if (name != null && !name.isEmpty()) {
      params.addString("name", name);
    }
    String u = params.applyToUrl(subUrl(""));
    byte[] bodyBytes = body == null ? null : Json.toBytes(body);
    return http.call("POST", u, bodyBytes, body == null ? "" : CONTENT_TYPE_JSON, Timeout.SHORT)
        .thenApply(resp -> Json.parseData(resp.body(), dataClass));
  }

  /** POST with a raw body (optional) and content type, unwrapping the data envelope. */
  public <T> CompletableFuture<T> postWithBody(
      String subPath,
      QueryParams params,
      byte[] body,
      String contentType,
      Class<T> dataClass,
      Timeout timeout) {
    return postWithBody(subPath, params, body, contentType, Json.type(dataClass), timeout);
  }

  public <T> CompletableFuture<T> postWithBody(
      String subPath,
      QueryParams params,
      byte[] body,
      String contentType,
      JavaType dataType,
      Timeout timeout) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    return http.call("POST", u, body, contentType, timeout)
        .thenApply(resp -> Json.<T>parseData(resp.body(), dataType));
  }

  /**
   * POST with a raw body, parsing the response body directly <em>without</em> unwrapping a {@code
   * {"data": ...}} envelope. Used by endpoints (e.g. actor input validation) whose response is a
   * plain object rather than the standard data envelope.
   */
  public <T> CompletableFuture<T> postWithBodyNoEnvelope(
      String subPath,
      QueryParams params,
      byte[] body,
      String contentType,
      Class<T> dataClass,
      Timeout timeout) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    return http.call("POST", u, body, contentType, timeout)
        .thenApply(resp -> Json.parse(resp.body(), dataClass));
  }

  /** PUT with a raw body (optional) and content type, unwrapping the data envelope. */
  public <T> CompletableFuture<T> putWithBody(
      String subPath,
      QueryParams params,
      byte[] body,
      String contentType,
      Class<T> dataClass,
      Timeout timeout) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    return http.call("PUT", u, body, contentType, timeout)
        .thenApply(resp -> Json.parseData(resp.body(), dataClass));
  }

  /**
   * PUT with a raw body and content type, parsing the response body directly <em>without</em>
   * unwrapping a {@code {"data": ...}} envelope. Used by endpoints (e.g. actor task input) whose
   * response is a plain object rather than the standard data envelope.
   */
  public <T> CompletableFuture<T> putWithBodyNoEnvelope(
      String subPath,
      QueryParams params,
      byte[] body,
      String contentType,
      Class<T> dataClass,
      Timeout timeout) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    return http.call("PUT", u, body, contentType, timeout)
        .thenApply(resp -> Json.parse(resp.body(), dataClass));
  }

  /** DELETE with a JSON body (used for batch request deletion), unwrapping the data envelope. */
  public <T> CompletableFuture<T> deleteWithBody(
      String subPath, QueryParams params, Object body, Class<T> dataClass, Timeout timeout) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    return http.call("DELETE", u, Json.toBytes(body), CONTENT_TYPE_JSON, timeout)
        .thenApply(resp -> Json.parseData(resp.body(), dataClass));
  }

  /**
   * GET returning the raw response (no data envelope). Completes with {@code null} on not-found.
   */
  public CompletableFuture<ApiResponse> getRaw(
      String subPath, QueryParams params, Timeout timeout) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    return http.call("GET", u, null, "", timeout)
        .handle(
            (resp, error) -> {
              if (error == null) {
                return resp;
              }
              Throwable cause = HttpClientCore.unwrapCompletion(error);
              if (cause instanceof ApifyApiException apiError && isNotFound(apiError)) {
                return null;
              }
              throw asRuntimeException(cause);
            });
  }

  /**
   * As {@link #getRaw}, but a 404 is not swallowed - it propagates as an {@link ApifyApiException}.
   */
  public CompletableFuture<ApiResponse> getRawRequired(
      String subPath, QueryParams params, Timeout timeout) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    return http.call("GET", u, null, "", timeout);
  }

  /**
   * Like {@link #getRaw}, but - mirroring {@link #getResourceUnlessAmbiguous} - a 404 is only
   * swallowed to {@code null} when this context addresses its resource by an explicit id.
   */
  public CompletableFuture<ApiResponse> getRawUnlessAmbiguous(
      String subPath, QueryParams params, Timeout timeout) {
    return ownId ? getRaw(subPath, params, timeout) : getRawRequired(subPath, params, timeout);
  }

  /** HEAD request; completes with whether the resource exists. */
  public CompletableFuture<Boolean> headExists(
      String subPath, QueryParams params, Timeout timeout) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    return http.call("HEAD", u, null, "", timeout)
        .handle(
            (resp, error) -> {
              if (error == null) {
                return true;
              }
              Throwable cause = HttpClientCore.unwrapCompletion(error);
              if (cause instanceof ApifyApiException apiError && isNotFound(apiError)) {
                return false;
              }
              throw asRuntimeException(cause);
            });
  }

  /** PUT with raw bytes and a content type (used for key-value-store record uploads). */
  public CompletableFuture<Void> putRaw(
      String subPath, QueryParams params, byte[] body, String contentType) {
    return putRaw(subPath, params, body, contentType, Timeout.SHORT, false);
  }

  /**
   * As {@link #putRaw(String, QueryParams, byte[], String)}, with an explicit per-request {@code
   * timeout} and control over whether transport timeouts are retried. Used by key-value-store
   * record uploads that expose the reference client's {@code timeoutSecs}/{@code
   * doNotRetryTimeouts} write options.
   */
  public CompletableFuture<Void> putRaw(
      String subPath,
      QueryParams params,
      byte[] body,
      String contentType,
      Timeout timeout,
      boolean doNotRetryTimeouts) {
    return putRaw(subPath, params, body, contentType, null, timeout, doNotRetryTimeouts);
  }

  /**
   * As {@link #putRaw(String, QueryParams, byte[], String, Timeout, boolean)}, additionally setting
   * {@code extraHeaders} on the request (used to forward a caller-supplied {@code Content-Encoding}
   * on a key-value-store record upload - see {@link HttpHeaders#CONTENT_ENCODING} - which also
   * stops {@link HttpClientCore} from compressing the already-encoded body again).
   */
  public CompletableFuture<Void> putRaw(
      String subPath,
      QueryParams params,
      byte[] body,
      String contentType,
      Map<String, String> extraHeaders,
      Timeout timeout,
      boolean doNotRetryTimeouts) {
    String u = mergedParams(params).applyToUrl(subUrl(subPath));
    return http.call("PUT", u, body, contentType, extraHeaders, timeout, doNotRetryTimeouts)
        .thenApply(resp -> null);
  }

  /**
   * Polls a GET endpoint with {@code waitForFinish} until the resource reaches a terminal state or
   * the wait budget elapses. {@code waitSecs == null} means "wait indefinitely", implemented as a
   * finite but very large bound so the polling always terminates.
   *
   * <p>The budget is a pure time bound, evaluated independently of whether the resource is
   * currently present: a just-started run/build can transiently return 404 (database-replica lag),
   * which is treated as "not yet available" - but only for up to {@link
   * #MAX_WAIT_FOR_MISSING_RESOURCE_MILLIS}. A resource that keeps 404-ing past that almost
   * certainly never existed (e.g. a typo'd id), so continuing to poll it for the rest of a long or
   * indefinite wait budget would only delay reporting that; past the bound, the 404 is rethrown
   * instead.
   *
   * <p>Each poll is chained via {@link CompletableFuture#thenCompose}, and the inter-poll delay is
   * a scheduled timer (see {@link Async}), so waiting never blocks a thread.
   */
  public <T> CompletableFuture<T> waitForFinish(
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
    // The polls have no timeout, and a withTimeout override does not apply to them: it would cut
    // every hold short (a 5 s override would poll four times a second), and cancelling the returned
    // future is the way out of a poll that never answers.
    ResourceContext polling =
        new ResourceContext(
            http.withoutTimeoutOverride(), url, baseParams, apiOrigin, publicOrigin, ownId);
    return polling.pollOnce(dataType, isTerminal, resourceName, start, budgetMillis, null, null);
  }

  /**
   * The outcome of a single {@code waitForFinish} poll: either a usable fetched value, or "absent"
   * - a 404 ({@code notFoundError} set), or a 200 whose {@code data} was {@code null} (tolerated
   * the same as "not yet available", not a programming error, matching {@link #getResource}'s own
   * null-tolerant handling of that case).
   */
  private record PollOutcome<T>(T value, ApifyApiException notFoundError) {
    static <T> PollOutcome<T> of(T value) {
      return new PollOutcome<>(value, null);
    }

    static <T> PollOutcome<T> notFound(ApifyApiException error) {
      return new PollOutcome<>(null, error);
    }

    boolean isAbsent() {
      return value == null;
    }
  }

  /**
   * One poll of the {@code waitForFinish} loop. {@code lastSeen} carries the most recently observed
   * resource and {@code absentSinceMillis} the instant it was first observed absent (404, or a null
   * {@code data} body), both across recursive calls (there is no shared mutable state between
   * polls, so each recursive step gets everything it needs as a parameter).
   */
  private <T> CompletableFuture<T> pollOnce(
      JavaType dataType,
      Predicate<T> isTerminal,
      String resourceName,
      long start,
      long budgetMillis,
      T lastSeen,
      Long absentSinceMillis) {
    long elapsed = System.currentTimeMillis() - start;
    long remainingSecs = (budgetMillis - elapsed) / 1000L;
    long requestSecs = Math.min(Math.max(remainingSecs, 0), WAIT_REQUEST_SECS);

    QueryParams params = new QueryParams();
    params.addLong("waitForFinish", requestSecs);

    return this.<T>getResourceRequired("", params, dataType, Timeout.NO_TIMEOUT)
        .handle(
            (value, error) -> {
              if (error == null) {
                return PollOutcome.of(value);
              }
              Throwable cause = HttpClientCore.unwrapCompletion(error);
              if (cause instanceof ApifyApiException apiError && isNotFound(apiError)) {
                return PollOutcome.<T>notFound(apiError);
              }
              throw asRuntimeException(cause);
            })
        .thenCompose(
            outcome -> {
              long now = System.currentTimeMillis();
              if (!outcome.isAbsent() && isTerminal.test(outcome.value())) {
                return CompletableFuture.completedFuture(outcome.value());
              }
              T seen = outcome.isAbsent() ? lastSeen : outcome.value();
              Long absentSince =
                  outcome.isAbsent() ? (absentSinceMillis != null ? absentSinceMillis : now) : null;
              boolean missingTooLong =
                  absentSince != null && now - absentSince >= MAX_WAIT_FOR_MISSING_RESOURCE_MILLIS;
              if (missingTooLong || now - start >= budgetMillis) {
                if (seen != null) {
                  return CompletableFuture.completedFuture(seen);
                }
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(
                    outcome.notFoundError() != null
                        ? outcome.notFoundError()
                        : new IllegalStateException(
                            "waiting for "
                                + resourceName
                                + " to finish failed: cannot fetch "
                                + resourceName
                                + " details from the server"));
                return failed;
              }
              return Async.delay(WAIT_POLL_INTERVAL)
                  .thenCompose(
                      unused ->
                          pollOnce(
                              dataType,
                              isTerminal,
                              resourceName,
                              start,
                              budgetMillis,
                              seen,
                              absentSince));
            });
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

  /** Rethrows a cause classified from a future's completion as an unchecked exception, as-is. */
  private static RuntimeException asRuntimeException(Throwable cause) {
    if (cause instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    // Every failure this client's async pipeline produces is already a RuntimeException
    // (ApifyApiException/ApifyTransportException); wrapping here is a defensive fallback only.
    return new ApifyTransportException(cause);
  }

  /**
   * Encodes a resource id so it is safe to embed in a URL path. Apify uses the {@code
   * username~resourcename} form, so every {@code /} in an id is replaced with {@code ~} (not just
   * the first: an id with more than one slash must not leave any literal {@code /} that could
   * restructure the request path).
   *
   * <p>Public (not just intra-package) because {@code RunClient#metamorph} in the {@code .run}
   * package needs to apply the same normalization to a resource id supplied as an argument value
   * (as opposed to the id embedded in this client's own URL path, which {@link #single} already
   * normalizes internally) — a cross-package caller of internal plumbing, same pattern as every
   * other member here promoted narrowly for a specific caller (see the package-split rationale in
   * this class's other narrowly-public members).
   */
  public static String toSafeId(String id) {
    return id.replace('/', '~');
  }

  /**
   * Percent-encodes a single URL path segment, so that values interpolated into the path (record
   * keys, request IDs) cannot break out of the segment.
   *
   * <p>Rejects an empty string or a bare {@code "."}/{@code ".."} segment instead of encoding it: a
   * URL parser (client-side, or a proxy/server in front of the API) resolves dot segments
   * <em>after</em> percent-decoding, so an encoded {@code "%2E%2E"} would still collapse against
   * its neighbours exactly like a literal {@code ".."} once decoded. Rejecting it outright - rather
   * than silently encoding something that cannot safely address any record/request - mirrors the
   * reference JS client's path-segment sanitization.
   */
  public static String encodePathSegment(String input) {
    if (input == null || input.isEmpty() || ".".equals(input) || "..".equals(input)) {
      throw new IllegalArgumentException(
          "a URL path segment must not be empty or a dot segment (\".\" or \"..\"), was: " + input);
    }
    // URLEncoder targets form encoding: it writes a space as "+" and escapes "~" and the
    // characters of encodeURIComponent's unreserved set, none of which a path segment needs.
    return URLEncoder.encode(input, StandardCharsets.UTF_8)
        .replace("+", "%20")
        .replace("%7E", "~")
        .replace("%21", "!")
        .replace("%27", "'")
        .replace("%28", "(")
        .replace("%29", ")");
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
