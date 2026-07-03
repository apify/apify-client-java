package com.apify.client;

/** A client for the key-value store collection ({@code GET/POST /v2/key-value-stores}). */
public final class KeyValueStoreCollectionClient {
  private final ResourceContext ctx;

  KeyValueStoreCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, "key-value-stores");
  }

  /** Lists key-value stores. */
  public PaginationList<KeyValueStore> list(StorageListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, KeyValueStore.class);
  }

  /**
   * Gets the store with the given name, creating it if it does not exist. An empty/{@code null}
   * name creates a new unnamed store.
   */
  public KeyValueStore getOrCreate(String name) {
    return ctx.getOrCreateNamed(name, KeyValueStore.class);
  }
}
