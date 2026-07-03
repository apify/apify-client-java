package com.apify.client;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;

/** A client for a specific dataset (and run-nested variants). */
public final class DatasetClient {
  private final HttpClientCore http;
  private ResourceContext ctx;

  DatasetClient(HttpClientCore http, String baseUrl, String resourcePath, String id) {
    this.http = http;
    this.ctx = ResourceContext.single(http, baseUrl, resourcePath, id);
  }

  private DatasetClient(HttpClientCore http, ResourceContext ctx) {
    this.http = http;
    this.ctx = ctx;
  }

  /** Creates a dataset client for a run's default dataset (nested path only, no ID). */
  static DatasetClient nested(HttpClientCore http, String base, String subPath) {
    return nested(http, base, subPath, null);
  }

  /** As {@link #nested(HttpClientCore, String, String)} but inheriting parent query params. */
  static DatasetClient nested(
      HttpClientCore http, String base, String subPath, QueryParams inherited) {
    return new DatasetClient(
        http, ResourceContext.collection(http, base, subPath).seedParams(inherited));
  }

  DatasetClient withPublicBase(String publicBaseUrl) {
    this.ctx = ctx.withPublicOrigin(publicBaseUrl);
    return this;
  }

  /** Fetches the dataset metadata, or empty if it does not exist. */
  public Optional<Dataset> get() {
    return ctx.getResource("", new QueryParams(), Dataset.class);
  }

  /** Updates the dataset metadata (e.g. name, title) and returns the updated object. */
  public Dataset update(Object newFields) {
    return ctx.updateResource("", newFields, Dataset.class);
  }

  /** Deletes the dataset. */
  public void delete() {
    ctx.deleteResource("");
  }

  /**
   * Lists items from the dataset, decoding each into a generic {@link JsonNode}. For typed decoding
   * use {@link #listItems(DatasetListItemsOptions, Class)}.
   */
  public PaginationList<JsonNode> listItems(DatasetListItemsOptions options) {
    return listItems(options, JsonNode.class);
  }

  /**
   * Lists items from the dataset, decoding each item into {@code itemClass}.
   *
   * <p>The dataset items endpoint returns a bare JSON array (not a data envelope) and reports
   * pagination via {@code X-Apify-Pagination-*} headers, surfaced in the returned {@link
   * PaginationList}.
   */
  public <T> PaginationList<T> listItems(DatasetListItemsOptions options, Class<T> itemClass) {
    QueryParams params = new QueryParams();
    options.apply(params);
    String url = ctx.mergedParams(params).applyToUrl(ctx.subUrl("items"));
    ApiResponse resp = http.call("GET", url, null, "", http.baseRequestTimeout());

    JavaType listType = Json.parametric(List.class, Json.type(itemClass));
    List<T> items = Json.parse(resp.body, listType);
    long count = items.size();

    PaginationList<T> result = new PaginationList<>();
    result.setItems(items);
    result.setCount(count);
    result.setTotal(headerLong(resp, "X-Apify-Pagination-Total", count));
    result.setOffset(headerLong(resp, "X-Apify-Pagination-Offset", 0));
    result.setLimit(headerLong(resp, "X-Apify-Pagination-Limit", count));
    if (options.descValue() != null) {
      result.setDesc(options.descValue());
    }
    return result;
  }

  /**
   * Downloads dataset items serialized in the given format, returning the raw bytes. Unlike {@link
   * #listItems} (parsed items), this returns the items already serialized to JSON, CSV, XLSX, XML,
   * RSS or HTML — useful for exporting.
   */
  public byte[] downloadItems(DownloadItemsFormat format, DatasetDownloadOptions options) {
    QueryParams params = new QueryParams();
    params.addString("format", format.wireValue());
    options.apply(params);
    String url = ctx.mergedParams(params).applyToUrl(ctx.subUrl("items"));
    ApiResponse resp = http.call("GET", url, null, "", http.baseRequestTimeout());
    return resp.body;
  }

  /**
   * Pushes one or more items to the dataset. {@code items} must serialize to a JSON object or an
   * array of objects.
   */
  public void pushItems(Object items) {
    http.call(
        "POST",
        ctx.subUrl("items"),
        Json.toBytes(items),
        ResourceContext.CONTENT_TYPE_JSON_CHARSET,
        http.baseRequestTimeout());
  }

  /** Returns statistical information about the dataset, or empty if unavailable. */
  public Optional<JsonNode> getStatistics() {
    ApiResponse resp = ctx.getRaw("statistics", new QueryParams());
    if (resp == null) {
      return Optional.empty();
    }
    return Optional.of(Json.parseData(resp.body, JsonNode.class));
  }

  /**
   * Builds a public URL for downloading this dataset's items.
   *
   * <p>It fetches the dataset, and if the dataset exposes a URL-signing secret key (i.e. it is
   * private), appends an HMAC-SHA256 signature so the URL grants access without an API token.
   * {@code expiresInSecs} optionally bounds the validity of a signed URL ({@code null} for
   * non-expiring). The URL is built from the configured public base URL.
   */
  public String createItemsPublicUrl(DatasetListItemsOptions options, Long expiresInSecs) {
    QueryParams params = new QueryParams();
    options.apply(params);
    Optional<Dataset> dataset = get();
    if (dataset.isPresent()) {
      String secret = extractString(dataset.get().getExtra(), "urlSigningSecretKey");
      if (secret != null) {
        String sig = Signatures.signStorageContent(secret, dataset.get().getId(), expiresInSecs);
        params.addString("signature", sig);
      }
    }
    return params.applyToUrl(ctx.publicUrl("items"));
  }

  private static long headerLong(ApiResponse resp, String name, long fallback) {
    return resp.headers.firstValueAsLong(name).orElse(fallback);
  }

  /** Reads a string field from an extra map, returning {@code null} if absent or not a string. */
  static String extractString(java.util.Map<String, Object> extra, String key) {
    Object v = extra.get(key);
    return (v instanceof String) ? (String) v : null;
  }
}
