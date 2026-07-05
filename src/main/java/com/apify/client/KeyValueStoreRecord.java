package com.apify.client;

/**
 * A single record retrieved from a key-value store. Its {@link #getValue() value} holds the raw
 * bytes; callers can decode it according to {@link #getContentType() content type}.
 */
public final class KeyValueStoreRecord {
  private final String key;
  private final byte[] value;
  private final String contentType;

  KeyValueStoreRecord(String key, byte[] value, String contentType) {
    this.key = key;
    // Defensive copy: this value type must not alias caller-visible mutable state.
    this.value = value == null ? null : value.clone();
    this.contentType = contentType;
  }

  /** The record key. */
  public String getKey() {
    return key;
  }

  /** The raw record bytes (a fresh copy on each call; mutating it does not affect the record). */
  public byte[] getValue() {
    return value == null ? null : value.clone();
  }

  /** The record's MIME type, as reported by the API. */
  public String getContentType() {
    return contentType;
  }
}
