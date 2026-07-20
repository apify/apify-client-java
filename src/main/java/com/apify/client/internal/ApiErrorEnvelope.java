package com.apify.client.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * The {@code {"error": {...}}} envelope the API returns for a non-success response, deserialized
 * directly by Jackson (in place of manual {@code JsonNode} field-by-field navigation). Internal to
 * the client.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ApiErrorEnvelope {

  public Body error;

  /** The nested {@code error} object: a machine-readable type, a message, and optional data. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Body {
    public String type;
    public String message;
    public Map<String, Object> data;
  }
}
