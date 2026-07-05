package com.apify.client;

import java.net.http.HttpHeaders;

/**
 * The parsed result of a single API call: the status code, headers and the fully-buffered response
 * body. Internal to the client.
 */
final class ApiResponse {
  final int statusCode;
  final HttpHeaders headers;
  final byte[] body;

  ApiResponse(int statusCode, HttpHeaders headers, byte[] body) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.body = body;
  }
}
