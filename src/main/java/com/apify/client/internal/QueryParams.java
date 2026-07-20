package com.apify.client.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * An ordered collection of query parameters that omits absent ({@code null}) values and encodes
 * booleans as {@code 1}/{@code 0}, matching the Apify API conventions. Internal to the client.
 */
public final class QueryParams {

  private final List<Pair> pairs = new ArrayList<>();

  public QueryParams() {}

  /** A single already-encoded (not yet URL-escaped) query parameter key/value pair. */
  private record Pair(String key, String value) {}

  /** Adds a string parameter if the value is non-null. */
  public QueryParams addString(String key, String value) {
    if (value != null) {
      pairs.add(new Pair(key, value));
    }
    return this;
  }

  /** Adds an integer parameter if the value is non-null. */
  public QueryParams addLong(String key, Long value) {
    if (value != null) {
      pairs.add(new Pair(key, Long.toString(value)));
    }
    return this;
  }

  /** Adds a floating-point parameter if the value is non-null. */
  public QueryParams addDouble(String key, Double value) {
    if (value != null) {
      pairs.add(new Pair(key, Double.toString(value)));
    }
    return this;
  }

  /** Adds a boolean parameter, encoded as {@code 1}/{@code 0}, if the value is non-null. */
  public QueryParams addBool(String key, Boolean value) {
    if (value != null) {
      pairs.add(new Pair(key, value ? "1" : "0"));
    }
    return this;
  }

  /** Adds a comma-joined list parameter if the list is non-null and non-empty. */
  public QueryParams addCsv(String key, List<String> values) {
    if (values != null && !values.isEmpty()) {
      pairs.add(new Pair(key, String.join(",", values)));
    }
    return this;
  }

  /** Appends an already-stringified key/value pair unconditionally. */
  public QueryParams addRaw(String key, String value) {
    pairs.add(new Pair(key, value));
    return this;
  }

  boolean isEmpty() {
    return pairs.isEmpty();
  }

  /** Returns a shallow copy of this instance. */
  QueryParams copy() {
    QueryParams out = new QueryParams();
    out.pairs.addAll(pairs);
    return out;
  }

  /** Appends all pairs from {@code other} to this instance. */
  public QueryParams extend(QueryParams other) {
    if (other != null) {
      pairs.addAll(other.pairs);
    }
    return this;
  }

  /** Appends the parameters to {@code rawUrl} as a URL-encoded query string. */
  public String applyToUrl(String rawUrl) {
    if (pairs.isEmpty()) {
      return rawUrl;
    }
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < pairs.size(); i++) {
      if (i > 0) {
        b.append('&');
      }
      Pair p = pairs.get(i);
      b.append(encode(p.key())).append('=').append(encode(p.value()));
    }
    String sep = rawUrl.contains("?") ? "&" : "?";
    return rawUrl + sep + b;
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
