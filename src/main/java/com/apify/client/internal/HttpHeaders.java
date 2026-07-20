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

  /** The request/response body media-type header name. */
  public static final String CONTENT_TYPE = "Content-Type";

  private HttpHeaders() {}
}
