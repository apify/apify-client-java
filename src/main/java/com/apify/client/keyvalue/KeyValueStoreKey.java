package com.apify.client.keyvalue;

import com.apify.client.ApifyResource;
import com.apify.client.internal.NormalizedUrlDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/** A single key listed from a key-value store. */
public final class KeyValueStoreKey extends ApifyResource {
  private String key;
  private long size;

  @JsonDeserialize(using = NormalizedUrlDeserializer.class)
  private String recordPublicUrl;

  /** The record key. */
  public String getKey() {
    return key;
  }

  /** The record size in bytes. */
  public long getSize() {
    return size;
  }

  /** A public URL of the record (signed when the store is not publicly accessible; normalized). */
  public String getRecordPublicUrl() {
    return recordPublicUrl;
  }
}
