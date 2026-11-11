package com.apify.client.http;

import com.apify.client.internal.Json;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;

/**
 * The parsed result of a single API call: the status code, headers and the fully-buffered response
 * body.
 *
 * <p>The {@code body} array is exposed without a defensive copy; see the header of {@code
 * spotbugs-exclude.xml}.
 *
 * @param statusCode the HTTP status code
 * @param headers the response headers
 * @param body the fully-buffered response body
 */
public record ApiResponse(int statusCode, HttpHeaders headers, byte[] body) {

  /** The body decoded as UTF-8 text; an empty string for a response without a body. */
  public String bodyAsString() {
    return new String(body, StandardCharsets.UTF_8);
  }

  /**
   * The body parsed as JSON into {@code type}, for example {@code JsonNode.class} or a POJO.
   *
   * @throws tools.jackson.core.JacksonException if the body is not valid JSON for {@code type}
   */
  public <T> T bodyAs(Class<T> type) {
    return Json.parse(body, type);
  }
}
