package com.apify.client.dataset;

import com.apify.client.PaginationList;
import com.apify.client.http.ApiResponse;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.AsyncPaginatedPublisher;
import com.apify.client.internal.Extras;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.Json;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import com.apify.client.internal.Signatures;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;

/** A client for a specific dataset (and run-nested variants). */
public final class DatasetClient {

  /** Header reporting the total number of items available (not just in this page). */
  private static final String HEADER_PAGINATION_TOTAL = "X-Apify-Pagination-Total";

  /** Header reporting the offset this page started at. */
  private static final String HEADER_PAGINATION_OFFSET = "X-Apify-Pagination-Offset";

  /** Header reporting the effective page size limit applied to this page. */
  private static final String HEADER_PAGINATION_LIMIT = "X-Apify-Pagination-Limit";

  /**
   * Header reporting the number of rows the API scanned to produce this page, before any item-level
   * filtering ({@code clean}/{@code skipEmpty}/{@code skipHidden}) is applied. Absent on older API
   * responses; see {@link PaginationList#getScannedCount()}.
   */
  private static final String HEADER_PAGINATION_COUNT = "X-Apify-Pagination-Count";

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

  /**
   * Fetches the dataset metadata, or empty if it does not exist.
   *
   * <p>On a client reached through a run/task without an explicit dataset id (e.g. {@code
   * run.dataset()}), a 404 is ambiguous between "the run is gone" and "the run has no dataset", so
   * it throws {@link com.apify.client.http.NotFoundError} instead of resolving to empty.
   */
  public CompletableFuture<Optional<Dataset>> get() {
    return ctx.getResourceUnlessAmbiguous("", new QueryParams(), Dataset.class);
  }

  /** Updates the dataset metadata (e.g. name, title) and returns the updated object. */
  public CompletableFuture<Dataset> update(Object newFields) {
    return ctx.updateResource("", newFields, Dataset.class);
  }

  /**
   * Deletes the dataset. See {@link #get()} for why a client without an explicit dataset id throws
   * on a 404 instead of treating it as a no-op.
   */
  public CompletableFuture<Void> delete() {
    return ctx.deleteResourceUnlessAmbiguous("");
  }

  /**
   * Lists items from the dataset, decoding each into a generic {@link JsonNode}. For typed decoding
   * use {@link #listItems(DatasetListItemsOptions, Class)}.
   */
  public CompletableFuture<PaginationList<JsonNode>> listItems(DatasetListItemsOptions options) {
    return listItems(options, JsonNode.class);
  }

  /**
   * Lists items from the dataset, decoding each item into {@code itemClass}.
   *
   * <p>The dataset items endpoint returns a bare JSON array (not a data envelope) and reports
   * pagination via {@code X-Apify-Pagination-*} headers, surfaced in the returned {@link
   * PaginationList}.
   */
  public <T> CompletableFuture<PaginationList<T>> listItems(
      DatasetListItemsOptions options, Class<T> itemClass) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return fetchItemsPage(params, options.descValue(), itemClass);
  }

  /**
   * Returns a lazy, backpressure-aware publisher over the dataset's items, decoding each into a
   * generic {@link JsonNode}. For typed decoding use {@link #iterateItems(DatasetListItemsOptions,
   * Long, Class)}.
   */
  public Flow.Publisher<JsonNode> iterateItems(DatasetListItemsOptions options, Long chunkSize) {
    return iterateItems(options, chunkSize, JsonNode.class);
  }

  /** As {@link #iterateItems(DatasetListItemsOptions, Long)} with the server-default page size. */
  public Flow.Publisher<JsonNode> iterateItems(DatasetListItemsOptions options) {
    return iterateItems(options, null, JsonNode.class);
  }

  /**
   * Returns a lazy, backpressure-aware publisher over the dataset's items, decoding each into
   * {@code itemClass}, fetching pages on demand. The options' {@code limit} caps the total number
   * of items yielded ({@code null} or non-positive = all); {@code chunkSize} is the per-request
   * page size ({@code null} = server default).
   *
   * <p>A server-side item filter ({@code skipEmpty}, {@code skipHidden}, {@code clean}, {@code
   * simplified}) or {@code unwind} can make a page's returned item count diverge from the number of
   * rows actually scanned - a filter drops rows (returns fewer), {@code unwind} splits one row's
   * array field into several items (returns more). Where the API reports the scanned count ({@code
   * X-Apify-Pagination-Count}), this iterator advances by that number rather than by the number of
   * items returned, so neither case repeats already-seen items, skips rows, nor ends iteration
   * early. See {@link PaginationList#getScannedCount()} for the one case that header value is not
   * trusted (and why).
   */
  public <T> Flow.Publisher<T> iterateItems(
      DatasetListItemsOptions options, Long chunkSize, Class<T> itemClass) {
    DatasetListItemsOptions opts = options != null ? options : new DatasetListItemsOptions();
    // Snapshot the filters/offset/limit/desc once so mutating the options mid-iteration cannot
    // leak.
    QueryParams filters = new QueryParams();
    opts.applyFilters(filters);
    Boolean desc = opts.descValue();
    return new AsyncPaginatedPublisher<>(
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
  private <T> CompletableFuture<PaginationList<T>> fetchItemsPage(
      QueryParams params, Boolean desc, Class<T> itemClass) {
    String url = ctx.mergedParams(params).applyToUrl(ctx.subUrl("items"));
    return http.call("GET", url, null, "", http.baseRequestTimeout())
        .thenApply(
            resp -> {
              JavaType listType = Json.parametric(List.class, Json.type(itemClass));
              List<T> items = Json.parse(resp.body(), listType);
              long count = items.size();

              PaginationList<T> result = new PaginationList<>();
              result.setItems(items);
              result.setCount(count);
              result.setTotal(headerLong(resp, HEADER_PAGINATION_TOTAL, count));
              result.setOffset(headerLong(resp, HEADER_PAGINATION_OFFSET, 0));
              result.setLimit(headerLong(resp, HEADER_PAGINATION_LIMIT, count));
              result.setScannedCount(headerLongOrNull(resp, HEADER_PAGINATION_COUNT));
              if (desc != null) {
                result.setDesc(desc);
              }
              return result;
            });
  }

  /**
   * Downloads dataset items serialized in the given format, returning the raw bytes. Unlike {@link
   * #listItems} (parsed items), this returns the items already serialized to JSON, CSV, XLSX, XML,
   * RSS or HTML — useful for exporting.
   */
  public CompletableFuture<byte[]> downloadItems(
      DownloadItemsFormat format, DatasetDownloadOptions options) {
    QueryParams params = new QueryParams();
    params.addString("format", format.wireValue());
    options.apply(params);
    String url = ctx.mergedParams(params).applyToUrl(ctx.subUrl("items"));
    return http.call("GET", url, null, "", http.baseRequestTimeout()).thenApply(ApiResponse::body);
  }

  /**
   * Pushes one or more items to the dataset. {@code items} must serialize to a JSON object or an
   * array of objects.
   */
  public CompletableFuture<Void> pushItems(Object items) {
    // Route through mergedParams like every sibling method, so a context seeded with pinned filters
    // (e.g. actor(id).lastRun(...).dataset()) targets the same run's dataset on write as on read.
    String url = ctx.mergedParams(new QueryParams()).applyToUrl(ctx.subUrl("items"));
    return http.call(
            "POST",
            url,
            Json.toBytes(items),
            ResourceContext.CONTENT_TYPE_JSON_CHARSET,
            http.baseRequestTimeout())
        .thenApply(resp -> null);
  }

  /**
   * Returns statistical information about the dataset.
   *
   * <p>A 404 throws {@link com.apify.client.http.NotFoundError} rather than resolving to empty:
   * unlike {@link #get()}, there is no meaningful "statistics are absent" state distinct from "the
   * dataset is gone", so a missing dataset is reported as a failure here too.
   */
  public CompletableFuture<JsonNode> getStatistics() {
    return ctx.getResourceRequired("statistics", new QueryParams(), JsonNode.class);
  }

  /**
   * Builds a public URL for downloading this dataset's items.
   *
   * <p>It fetches the dataset, and if the dataset exposes a URL-signing secret key (i.e. it is
   * private), appends an HMAC-SHA256 signature so the URL grants access without an API token.
   * {@code expiresInSecs} optionally bounds the validity of a signed URL ({@code null} for
   * non-expiring). The URL is built from the configured public base URL.
   */
  public CompletableFuture<String> createItemsPublicUrl(
      DatasetListItemsOptions options, Long expiresInSecs) {
    return createItemsPublicUrl(options, expiresInSecs, null);
  }

  /**
   * As {@link #createItemsPublicUrl(DatasetListItemsOptions, Long)}, additionally setting the
   * {@code format} the URL serves items in (e.g. {@code csv}, {@code xml}); {@code null} leaves it
   * unset, which the API serves as {@code json}.
   */
  public CompletableFuture<String> createItemsPublicUrl(
      DatasetListItemsOptions options, Long expiresInSecs, DownloadItemsFormat format) {
    QueryParams params = new QueryParams();
    options.apply(params);
    if (format != null) {
      params.addString("format", format.wireValue());
    }
    return get()
        .thenApply(
            dataset -> {
              if (dataset.isPresent()) {
                String secret =
                    Extras.extractString(dataset.get().getExtra(), "urlSigningSecretKey");
                if (secret != null) {
                  String sig =
                      Signatures.signStorageContent(secret, dataset.get().getId(), expiresInSecs);
                  params.addString("signature", sig);
                }
              }
              // Mirror the JS reference: the public URL is this client's resource path plus the
              // signature (over the resolved concrete dataset id) and the explicit options — the
              // seeded status/origin filters are deliberately not carried. On a last-run-nested
              // client this keeps the unpinned ".../runs/last/dataset" path; see docs/storages.md
              // for that limitation.
              return params.applyToUrl(ctx.publicUrl("items"));
            });
  }

  private static long headerLong(ApiResponse resp, String name, long fallback) {
    return resp.headers().firstValueAsLong(name).orElse(fallback);
  }

  /**
   * As {@link #headerLong}, but returns {@code null} (rather than a fallback value) when the header
   * is absent, so callers can distinguish "not reported" from any particular number - notably
   * {@code 0}, a value the header can legitimately carry (see {@link
   * PaginationList#getScannedCount()}).
   */
  private static Long headerLongOrNull(ApiResponse resp, String name) {
    var value = resp.headers().firstValueAsLong(name);
    return value.isPresent() ? value.getAsLong() : null;
  }
}
