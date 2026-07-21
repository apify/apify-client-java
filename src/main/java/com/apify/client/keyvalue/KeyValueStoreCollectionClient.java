package com.apify.client.keyvalue;

import com.apify.client.StorageListOptions;
import com.apify.client.internal.AbstractCollectionClient;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.ResourceContext;

/** A client for the key-value store collection ({@code GET/POST /v2/key-value-stores}). */
public final class KeyValueStoreCollectionClient
    extends AbstractCollectionClient<KeyValueStore, StorageListOptions> {

  public KeyValueStoreCollectionClient(HttpClientCore http, String baseUrl) {
    super(
        ResourceContext.collection(http, baseUrl, ApiPaths.KEY_VALUE_STORES),
        KeyValueStore.class,
        StorageListOptions::new);
  }

  /**
   * Gets the store with the given name, creating it if it does not exist. An empty/{@code null}
   * name creates a new unnamed store.
   */
  public KeyValueStore getOrCreate(String name) {
    return getOrCreate(name, null);
  }

  /**
   * Gets the store with the given name, creating it if it does not exist, applying the given {@code
   * schema} on creation. Mirrors the reference client's {@code getOrCreate(name, {schema})}; a
   * {@code null} schema behaves like {@link #getOrCreate(String)}.
   */
  public KeyValueStore getOrCreate(String name, Object schema) {
    return ctx.getOrCreateNamedWithSchema(name, schema, KeyValueStore.class);
  }
}
