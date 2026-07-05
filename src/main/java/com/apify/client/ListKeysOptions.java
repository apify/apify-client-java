package com.apify.client;

/** Configures {@link KeyValueStoreClient#listKeys(ListKeysOptions)}. */
public final class ListKeysOptions {
  private Long limit;
  private String exclusiveStartKey;
  private String prefix;
  private String collection;
  private String signature;

  /** Maximum number of keys to return. */
  public ListKeysOptions limit(Long limit) {
    this.limit = limit;
    return this;
  }

  /** List keys after this one (for pagination). */
  public ListKeysOptions exclusiveStartKey(String exclusiveStartKey) {
    this.exclusiveStartKey = exclusiveStartKey;
    return this;
  }

  /** Restrict the listing to keys with this prefix. */
  public ListKeysOptions prefix(String prefix) {
    this.prefix = prefix;
    return this;
  }

  /** Restrict the listing to a named collection of keys. */
  public ListKeysOptions collection(String collection) {
    this.collection = collection;
    return this;
  }

  /** A pre-shared URL signature granting access without an API token. */
  public ListKeysOptions signature(String signature) {
    this.signature = signature;
    return this;
  }

  String signatureValue() {
    return signature;
  }

  void apply(QueryParams q) {
    q.addLong("limit", limit)
        .addString("exclusiveStartKey", exclusiveStartKey)
        .addString("prefix", prefix)
        .addString("collection", collection)
        .addString("signature", signature);
  }
}
