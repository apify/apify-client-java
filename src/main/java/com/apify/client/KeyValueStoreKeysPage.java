package com.apify.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

/** A page of keys from a key-value store. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class KeyValueStoreKeysPage {
  private long limit;
  private boolean isTruncated;
  private String exclusiveStartKey;
  private String nextExclusiveStartKey;
  private List<KeyValueStoreKey> items = List.of();

  /** The maximum number of keys requested. */
  public long getLimit() {
    return limit;
  }

  /** Whether more keys are available. */
  public boolean isTruncated() {
    return isTruncated;
  }

  /** The key the listing started after. */
  public String getExclusiveStartKey() {
    return exclusiveStartKey;
  }

  /** The key to pass to fetch the next page. */
  public String getNextExclusiveStartKey() {
    return nextExclusiveStartKey;
  }

  /** The listed keys. */
  public List<KeyValueStoreKey> getItems() {
    return Collections.unmodifiableList(items);
  }
}
