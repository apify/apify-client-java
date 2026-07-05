package com.apify.client;

/** A single key listed from a key-value store. */
public final class KeyValueStoreKey extends ApifyResource {
  private String key;
  private long size;

  /** The record key. */
  public String getKey() {
    return key;
  }

  /** The record size in bytes. */
  public long getSize() {
    return size;
  }
}
