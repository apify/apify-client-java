package com.apify.client.actor;

import com.apify.client.http.HttpCompressor;
import com.apify.client.http.Timeout;
import com.apify.client.internal.QueryParams;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configures {@link ActorServerClient#request(String, ActorServerRequestOptions)} and {@link
 * ActorServerClient#requestStream(String, ActorServerRequestOptions)}. All fields are optional: by
 * default the request is a {@code GET} without query parameters, headers or body, sent with the
 * {@link Timeout#LONG} timeout.
 */
public final class ActorServerRequestOptions {

  /**
   * The methods a request can use. {@code CONNECT} is left out, since the JDK HTTP client does not
   * send it.
   */
  private static final Set<String> METHODS =
      Set.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "TRACE", "PATCH");

  private static final String PARENT_REQUEST_ID_HEADER = "X-Actor-Request-ID";

  private String method = "GET";
  private final Map<String, Object> params = new LinkedHashMap<>();
  private final Map<String, String> headers = new LinkedHashMap<>();
  private Object body;
  private String parentRequestId;
  private HttpCompressor compression;
  private Timeout timeout = Timeout.LONG;

  /**
   * The HTTP method of the request, in upper case: {@code GET} (the default), {@code HEAD}, {@code
   * POST}, {@code PUT}, {@code DELETE}, {@code OPTIONS}, {@code TRACE} or {@code PATCH}.
   *
   * @throws IllegalArgumentException for any other method
   */
  public ActorServerRequestOptions method(String method) {
    if (method == null || !METHODS.contains(method)) {
      throw new IllegalArgumentException(
          "method must be one of " + METHODS.stream().sorted().toList() + ", was: " + method);
    }
    this.method = method;
    return this;
  }

  /**
   * Adds a query parameter. A {@code null} value is left out, a boolean is sent as {@code 1} or
   * {@code 0} and a collection as its comma-joined elements, as for API calls; any other value is
   * sent as its string form.
   */
  public ActorServerRequestOptions param(String name, Object value) {
    params.put(name, value);
    return this;
  }

  /** Adds all the entries of {@code params} as query parameters; see {@link #param}. */
  public ActorServerRequestOptions params(Map<String, ?> params) {
    this.params.putAll(params);
    return this;
  }

  /** Adds a header to the request. */
  public ActorServerRequestOptions header(String name, String value) {
    headers.put(name, value);
    return this;
  }

  /**
   * Adds all the entries of {@code headers} to the request. They reach the Actor's server as they
   * are, an {@code Authorization} header included: the client's token travels in the {@code
   * X-Apify-Authorization} header, which the platform consumes, so it never reaches the Actor's
   * code and the {@code Authorization} header stays free for the Actor's own authentication.
   */
  public ActorServerRequestOptions headers(Map<String, String> headers) {
    this.headers.putAll(headers);
    return this;
  }

  /**
   * The request body: a {@code byte[]} or a {@link String} is sent as it is (set its {@code
   * Content-Type} with {@link #header}), and anything else, a {@link Map}, a list or a POJO, is
   * serialized to JSON and sent as {@code application/json} unless a {@code Content-Type} header is
   * set.
   */
  public ActorServerRequestOptions body(Object body) {
    this.body = body;
    return this;
  }

  /**
   * The {@code X-Actor-Request-ID} header of the request your multi-tenant Server Actor is
   * handling. It is sent on as the same header, replacing one set in {@link #headers}, so the
   * called Actor charges at that request's pricing tier. Use a client created with the run's {@code
   * APIFY_TOKEN}.
   */
  public ActorServerRequestOptions parentRequestId(String parentRequestId) {
    this.parentRequestId = parentRequestId;
    return this;
  }

  /**
   * Compresses the request body with {@code compression}, under the same conditions as for API
   * calls: a body that is large enough and has a content type that is not already compressed, and
   * no {@code Content-Encoding} header of your own. Set it only if the Actor's server decodes the
   * resulting {@code Content-Encoding}: the body is sent uncompressed by default, since the server
   * may not.
   */
  public ActorServerRequestOptions compression(HttpCompressor compression) {
    this.compression = compression;
    return this;
  }

  /**
   * The timeout of each attempt. Defaults to {@link Timeout#LONG}, since the first request to an
   * idle Actor waits for the platform to start a server run. For {@link
   * ActorServerClient#requestStream} it bounds the wait for the server to start responding.
   */
  public ActorServerRequestOptions timeout(Timeout timeout) {
    if (timeout == null) {
      throw new IllegalArgumentException("timeout must not be null");
    }
    this.timeout = timeout;
    return this;
  }

  String methodValue() {
    return method;
  }

  Object bodyValue() {
    return body;
  }

  /**
   * The headers to send: the caller's, with the parent request ID header replacing any duplicate.
   */
  Map<String, String> headersValue() {
    if (parentRequestId == null) {
      return Collections.unmodifiableMap(headers);
    }
    Map<String, String> merged = new LinkedHashMap<>(headers);
    merged.keySet().removeIf(PARENT_REQUEST_ID_HEADER::equalsIgnoreCase);
    merged.put(PARENT_REQUEST_ID_HEADER, parentRequestId);
    return Collections.unmodifiableMap(merged);
  }

  HttpCompressor compressionValue() {
    return compression;
  }

  Timeout timeoutValue() {
    return timeout;
  }

  /** The {@code Content-Type} header the caller set, ignoring the case of its name, or null. */
  String contentTypeHeader() {
    return headers.entrySet().stream()
        .filter(entry -> "content-type".equals(entry.getKey().toLowerCase(Locale.ROOT)))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  /** The query parameters, converted as for API calls. */
  QueryParams queryParams() {
    QueryParams query = new QueryParams();
    params.forEach(
        (name, value) -> {
          if (value instanceof Boolean flag) {
            query.addBool(name, flag);
          } else if (value instanceof Collection<?> values) {
            query.addCsv(name, values.stream().map(String::valueOf).collect(Collectors.toList()));
          } else if (value != null) {
            query.addString(name, value.toString());
          }
        });
    return query;
  }
}
