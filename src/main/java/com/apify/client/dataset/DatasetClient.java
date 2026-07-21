package com.apify.client.dataset;

import com.apify.client.PaginationList;
import com.apify.client.http.ApiResponse;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.Extras;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.Json;
import com.apify.client.internal.PaginatedIterator;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import com.apify.client.internal.Signatures;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/** A client for a specific dataset (and run-nested variants). */
public final class DatasetClient {

  /** Header reporting the total number of items available (not just in this page). */
  private static final String HEADER_PAGINATION_TOTAL = "X-Apify-Pagination-Total";

  /** Header reporting the offset this page started at. */
  private static final String HEADER_PAGINATION_OFFSET = "X-Apify-Pagination-Offset";

  /** Header reporting the effective page size limit applied to this page. */
  private static final String HEADER_PAGINATION_LIMIT = "X-Apify-Pagination-Limit";

  private final HttpClientCore http;
  private final ResourceContext ctx;

  /** Creates a client for a dataset addressed by ID or name, reached only through the API host. */
  public DatasetClient(HttpClientCore http, String baseUrl, String id) {
    this(http, ResourceContext.single(http, baseUrl, ApiPaths.DATASETS, id));
  }

  /**
   * Creates a client for a dataset addressed by ID or name, whose {@code createItemsPublicUrl}
   * builds URLs against {@code publicBaseUrl} instead of {@code baseUrl}.
   */
  public DatasetClient(HttpClientCore http, String baseUrl, String id, String publicBaseUrl) {
    this(
        http,
        ResourceContext.single(http, baseUrl, ApiPaths.DATASETS, id)
            .withPublicOrigin(publicBaseUrl));
  }

  private DatasetClient(HttpClientCore http, ResourceContext ctx) {
    this.http = http;
    this.ctx = ctx;
  }

  /** Creates a dataset client for a run's default dataset (nested path only, no ID). */
  public static DatasetClient nested(HttpClientCore http, String base, String subPath) {
    return nested(http, base, subPath, null);
  }

  /** As {@link #nested(HttpClientCore, String, String)} but inheriting parent query params. */
  public static DatasetClient nested(
      HttpClientCore http, String base, String subPath, QueryParams inherited) {
    return new DatasetClient(
        http, ResourceContext.nestedCollection(http, base, subPath, inherited));
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
    return fetchItemsPage(params, options.descValue(), itemClass);
  }

  /**
   * Returns a lazy iterator over the dataset's items, decoding each into a generic {@link
   * JsonNode}. For typed decoding use {@link #iterateItems(DatasetListItemsOptions, Long, Class)}.
   */
  public Iterator<JsonNode> iterateItems(DatasetListItemsOptions options, Long chunkSize) {
    return iterateItems(options, chunkSize, JsonNode.class);
  }

  /** As {@link #iterateItems(DatasetListItemsOptions, Long)} with the server-default page size. */
  public Iterator<JsonNode> iterateItems(DatasetListItemsOptions options) {
    return iterateItems(options, null, JsonNode.class);
  }

  /**
   * Returns a lazy iterator over the dataset's items, decoding each into {@code itemClass},
   * fetching pages on demand. The options' {@code limit} caps the total number of items yielded
   * ({@code null} or non-positive = all); {@code chunkSize} is the per-request page size ({@code
   * null} = server default).
   *
   * <p>Note: server-side item filters ({@code skipEmpty}, {@code skipHidden}, {@code clean}, {@code
   * simplified}) are applied after {@code offset}/{@code limit}, so a page can return fewer items
   * than requested. Combining those filters with iteration can repeat items (overlapping windows)
   * and, if a whole offset window is filtered out, the endpoint returns an empty page which ends
   * iteration early — silently skipping the remaining items. Iterate without server-side item
   * filters, or page explicitly with {@link #listItems} and filter client-side.
   */
  public <T> Iterator<T> iterateItems(
      DatasetListItemsOptions options, Long chunkSize, Class<T> itemClass) {
    DatasetListItemsOptions opts = options != null ? options : new DatasetListItemsOptions();
    // Snapshot the filters/offset/limit/desc once so mutating the options mid-iteration cannot
    // leak.
    QueryParams filters = new QueryParams();
    opts.applyFilters(filters);
    Boolean desc = opts.descValue();
    return new PaginatedIterator<>(
        opts.limitValue(),
        chunkSize,
        opts.offsetValue(),
        (offset, pageLimit) -> {
          QueryParams p = new QueryParams().addLong("offset", offset).addLong("limit", pageLimit);
          p.extend(filters);
          return fetchItemsPage(p, desc, itemClass);
        });
  }

  /**
   * Fetches a single page of dataset items for the already-built query {@code params}. The dataset
   * items endpoint returns a bare JSON array (not a data envelope) and reports pagination via
   * {@code X-Apify-Pagination-*} headers, surfaced in the returned {@link PaginationList}.
   */
  private <T> PaginationList<T> fetchItemsPage(
      QueryParams params, Boolean desc, Class<T> itemClass) {
    String url = ctx.mergedParams(params).applyToUrl(ctx.subUrl("items"));
    ApiResponse resp = http.call("GET", url, null, "", http.baseRequestTimeout());

    JavaType listType = Json.parametric(List.class, Json.type(itemClass));
    List<T> items = Json.parse(resp.body(), listType);
    long count = items.size();

    PaginationList<T> result = new PaginationList<>();
    result.setItems(items);
    result.setCount(count);
    result.setTotal(headerLong(resp, HEADER_PAGINATION_TOTAL, count));
    result.setOffset(headerLong(resp, HEADER_PAGINATION_OFFSET, 0));
    result.setLimit(headerLong(resp, HEADER_PAGINATION_LIMIT, count));
    if (desc != null) {
      result.setDesc(desc);
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
    return resp.body();
  }

  /**
   * Pushes one or more items to the dataset. {@code items} must serialize to a JSON object or an
   * array of objects.
   */
  public void pushItems(Object items) {
    // Route through mergedParams like every sibling method, so a context seeded with pinned filters
    // (e.g. actor(id).lastRun(...).dataset()) targets the same run's dataset on write as on read.
    String url = ctx.mergedParams(new QueryParams()).applyToUrl(ctx.subUrl("items"));
    http.call(
        "POST",
        url,
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
    return Optional.of(Json.parseData(resp.body(), JsonNode.class));
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
      String secret = Extras.extractString(dataset.get().getExtra(), "urlSigningSecretKey");
      if (secret != null) {
        String sig = Signatures.signStorageContent(secret, dataset.get().getId(), expiresInSecs);
        params.addString("signature", sig);
      }
    }
    // Mirror the JS reference: the public URL is this client's resource path plus the signature
    // (over the resolved concrete dataset id) and the explicit options — the seeded status/origin
    // filters are deliberately not carried. On a last-run-nested client this keeps the unpinned
    // ".../runs/last/dataset" path; see docs/storages.md for that limitation.
    return params.applyToUrl(ctx.publicUrl("items"));
  }

  private static long headerLong(ApiResponse resp, String name, long fallback) {
    return resp.headers().firstValueAsLong(name).orElse(fallback);
  }
}
