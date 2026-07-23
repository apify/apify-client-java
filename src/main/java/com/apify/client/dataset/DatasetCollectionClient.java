package com.apify.client.dataset;

import com.apify.client.StorageListOptions;
import com.apify.client.internal.AbstractCollectionClient;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.ResourceContext;

/** A client for the dataset collection ({@code GET/POST /v2/datasets}). */
public final class DatasetCollectionClient
    extends AbstractCollectionClient<Dataset, StorageListOptions> {

  public DatasetCollectionClient(HttpClientCore http, String baseUrl) {
    super(
        ResourceContext.collection(http, baseUrl, ApiPaths.DATASETS),
        Dataset.class,
        StorageListOptions::new);
  }

  /**
   * Gets the dataset with the given name, creating it if it does not exist. An empty/{@code null}
   * name creates a new unnamed dataset.
   */
  public Dataset getOrCreate(String name) {
    return getOrCreate(name, null);
  }

  /**
   * Gets the dataset with the given name, creating it if it does not exist, applying the given
   * {@code schema} on creation. Mirrors the reference client's {@code getOrCreate(name, {schema})};
   * a {@code null} schema behaves like {@link #getOrCreate(String)}.
   */
  public Dataset getOrCreate(String name, Object schema) {
    return ctx.getOrCreateNamedWithSchema(name, schema, Dataset.class);
  }
}
