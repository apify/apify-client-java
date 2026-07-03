package com.apify.client;

/** A client for the dataset collection ({@code GET/POST /v2/datasets}). */
public final class DatasetCollectionClient {
  private final ResourceContext ctx;

  DatasetCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, "datasets");
  }

  /** Lists datasets. */
  public PaginationList<Dataset> list(StorageListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, Dataset.class);
  }

  /**
   * Gets the dataset with the given name, creating it if it does not exist. An empty/{@code null}
   * name creates a new unnamed dataset.
   */
  public Dataset getOrCreate(String name) {
    return ctx.getOrCreateNamed(name, Dataset.class);
  }
}
