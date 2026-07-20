package com.apify.client.dataset;

import com.apify.client.ApiPaths;
import com.apify.client.PaginationList;
import com.apify.client.QueryParams;
import com.apify.client.ResourceContext;
import com.apify.client.StorageListOptions;
import com.apify.client.http.HttpClientCore;
import java.util.Iterator;

/** A client for the dataset collection ({@code GET/POST /v2/datasets}). */
public final class DatasetCollectionClient {
  private final ResourceContext ctx;

  public DatasetCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, ApiPaths.DATASETS);
  }

  /** Lists datasets. */
  public PaginationList<Dataset> list(StorageListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, Dataset.class);
  }

  /**
   * Returns a lazy iterator over the datasets. The options' {@code limit} caps the total number
   * yielded ({@code null} or non-positive = all); {@code chunkSize} is the per-request page size
   * ({@code null} = server default).
   */
  public Iterator<Dataset> iterate(StorageListOptions options) {
    return iterate(options, null);
  }

  /**
   * As {@link #iterate(StorageListOptions)}, but {@code chunkSize} sets the per-request page size.
   */
  public Iterator<Dataset> iterate(StorageListOptions options, Long chunkSize) {
    StorageListOptions opts = options != null ? options : new StorageListOptions();
    return ctx.iterateResource(
        "", opts.limitValue(), chunkSize, opts.offsetValue(), opts::applyFilters, Dataset.class);
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
    Object body = schema == null ? null : java.util.Collections.singletonMap("schema", schema);
    return ctx.getOrCreateNamed(name, body, Dataset.class);
  }
}
