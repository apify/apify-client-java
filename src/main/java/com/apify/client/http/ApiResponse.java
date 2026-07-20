package com.apify.client.http;

import java.net.http.HttpHeaders;

/**
 * The parsed result of a single API call: the status code, headers and the fully-buffered response
 * body. Internal to the client.
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
