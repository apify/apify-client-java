package com.apify.client.http;

import java.net.http.HttpHeaders;

/**
 * The parsed result of a single API call: the status code, headers and the fully-buffered response
 * body.
 *
 * <p>This is a plain, public data carrier - it is legitimately exported, not internal plumbing:
 * {@code com.apify.client.internal}'s HTTP core builds it as the result of every call it makes, and
 * it is used as the local unwrapped-response type across the resource clients in this module. The
 * fields are exposed raw (no defensive copy of {@code body}) by design; see the {@code
 * spotbugs-exclude.xml} header for why that is a deliberate no-copy contract rather than an
 * oversight.
 */
public final class ApiResponse {
  public final int statusCode;
  public final HttpHeaders headers;
  public final byte[] body;

  /**
   * Public so the internal HTTP client (in a separate, non-exported package) can build one; end
   * users are not expected to construct this directly.
   */
  public ApiResponse(int statusCode, HttpHeaders headers, byte[] body) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.body = body;
  }
}
