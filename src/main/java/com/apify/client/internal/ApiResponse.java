package com.apify.client.internal;

import java.net.http.HttpHeaders;

/**
 * The parsed result of a single API call: the status code, headers and the fully-buffered response
 * body. Internal to the client.
 */
public final class ApiResponse {
  public final int statusCode;
  public final HttpHeaders headers;
  public final byte[] body;

  public ApiResponse(int statusCode, HttpHeaders headers, byte[] body) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.body = body;
  }
}
