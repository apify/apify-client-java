package com.apify.client.internal;

import java.util.Map;

/**
 * Helpers for reading typed values out of a model's {@code extra} map ({@link
 * com.apify.client.ApifyResource#getExtra()}). Shared by every resource client that needs a field
 * the model does not (yet) expose as a typed getter (DRY).
 */
public final class Extras {

  private Extras() {}

  /** Reads a string field from an extra map, returning {@code null} if absent or not a string. */
  public static String extractString(Map<String, Object> extra, String key) {
    Object v = extra.get(key);
    return (v instanceof String) ? (String) v : null;
  }
}
