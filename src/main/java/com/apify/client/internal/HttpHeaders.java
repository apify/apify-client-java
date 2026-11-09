package com.apify.client.internal;

/**
 * Names of the standard HTTP headers this client sets or reads on more than one call site, named
 * and collected in one place so every reader/writer references the same constant instead of
 * repeating the literal.
 */
public final class HttpHeaders {

  /** The mandated User-Agent request header name. */
  public static final String USER_AGENT = "User-Agent";

  /** The bearer-token authentication header name. */
  public static final String AUTHORIZATION = "Authorization";

  /**
   * The header that carries the client's token on a request to an Actor's server. The platform
   * consumes it before the request reaches the Actor's code, which keeps {@link #AUTHORIZATION}
   * free for the Actor's own authentication.
   */
  public static final String X_APIFY_AUTHORIZATION = "X-Apify-Authorization";

  /** The request/response body media-type header name. */
  public static final String CONTENT_TYPE = "Content-Type";

  /**
   * The request/response body compression header name. Set explicitly on a key-value-store {@code
   * setRecord} upload, it tells {@link HttpClientCore} the body is already encoded, which both
   * skips the client's own automatic compression and forwards the caller's encoding verbatim.
   */
  public static final String CONTENT_ENCODING = "Content-Encoding";

  private HttpHeaders() {}
}
