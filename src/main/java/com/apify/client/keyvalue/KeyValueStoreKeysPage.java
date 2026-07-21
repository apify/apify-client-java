package com.apify.client.keyvalue;

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

  /** The listed keys (never {@code null}; unmodifiable). */
  public List<KeyValueStoreKey> getItems() {
    // Null-coalesce: Jackson binds directly to the (private) `items` field for deserialization,
    // which bypasses the `= List.of()` field initializer whenever the API response contains an
    // explicit `"items": null`.
    return items == null ? List.of() : Collections.unmodifiableList(items);
  }
}
