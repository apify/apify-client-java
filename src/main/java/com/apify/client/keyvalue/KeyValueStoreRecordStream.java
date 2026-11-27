package com.apify.client.keyvalue;

import java.io.IOException;
import java.io.InputStream;

/**
 * A record retrieved from a key-value store whose {@link #getValue() value} is a live stream, so a
 * large record is processed in chunks instead of being held in memory. Close the record when done,
 * or read its stream to the end, to release the connection.
 */
public final class KeyValueStoreRecordStream implements AutoCloseable {
  private final String key;
  private final InputStream value;
  private final String contentType;

  KeyValueStoreRecordStream(String key, InputStream value, String contentType) {
    this.key = key;
    this.value = value;
    this.contentType = contentType;
  }

  /** The record key. */
  public String getKey() {
    return key;
  }

  /** The record bytes, as they arrive. The same stream is returned on every call. */
  public InputStream getValue() {
    return value;
  }

  /** The record's MIME type, as reported by the API. */
  public String getContentType() {
    return contentType;
  }

  /**
   * Closes the stream of the record.
   *
   * @throws IOException if the stream fails to close
   */
  @Override
  public void close() throws IOException {
    value.close();
  }
}
