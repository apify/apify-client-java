package com.apify.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

/** A client for a specific request queue (and run-nested variants). */
public final class RequestQueueClient {

  /** The API limit on requests per batch call; larger inputs are split into chunks of this size. */
  private static final int MAX_REQUESTS_PER_BATCH = 25;

  private final HttpClientCore http;
  private final ResourceContext ctx;
  private final String clientKey;

  RequestQueueClient(HttpClientCore http, String baseUrl, String resourcePath, String id) {
    this(http, ResourceContext.single(http, baseUrl, resourcePath, id), null);
  }

  private RequestQueueClient(HttpClientCore http, ResourceContext ctx, String clientKey) {
    this.http = http;
    this.ctx = ctx;
    this.clientKey = clientKey;
  }

  /** Creates a client for a run's default request queue (nested path only, no ID). */
  static RequestQueueClient nested(HttpClientCore http, String base, String subPath) {
    return new RequestQueueClient(http, ResourceContext.collection(http, base, subPath), null);
  }

  /**
   * Returns a copy of the client that identifies its requests with {@code clientKey}. A stable
   * client key is required to operate on locks the client itself created (e.g. to unlock its own
   * requests), and lets the API detect whether multiple clients access a queue.
   */
  public RequestQueueClient withClientKey(String clientKey) {
    return new RequestQueueClient(http, ctx, clientKey);
  }

  private QueryParams withClientKey(QueryParams params) {
    if (clientKey != null && !clientKey.isEmpty()) {
      params.addString("clientKey", clientKey);
    }
    return params;
  }

  /** Fetches the queue metadata, or empty if it does not exist. */
  public Optional<RequestQueue> get() {
    return ctx.getResource("", new QueryParams(), RequestQueue.class);
  }

  /** Updates the queue metadata (e.g. name) and returns the updated object. */
  public RequestQueue update(Object newFields) {
    return ctx.updateResource("", newFields, RequestQueue.class);
  }

  /** Deletes the queue. */
  public void delete() {
    ctx.deleteResource("");
  }

  /**
   * Returns the requests at the head (front) of the queue, up to {@code limit} ({@code null} for
   * the server default).
   */
  public RequestQueueHead listHead(Long limit) {
    QueryParams params = new QueryParams();
    params.addLong("limit", limit);
    withClientKey(params);
    return ctx.getResourceRequired("head", params, RequestQueueHead.class);
  }

  /** Adds a request to the queue. If {@code forefront} is true, it is added to the front. */
  public RequestQueueOperationInfo addRequest(RequestQueueRequest request, boolean forefront) {
    QueryParams params = new QueryParams();
    params.addBool("forefront", forefront);
    withClientKey(params);
    return ctx.postWithBody(
        "requests",
        params,
        Json.toBytes(request),
        ResourceContext.CONTENT_TYPE_JSON,
        RequestQueueOperationInfo.class);
  }

  /** Fetches a request by ID, or empty if it does not exist. */
  public Optional<RequestQueueRequest> getRequest(String id) {
    return ctx.getResource(
        "requests/" + ResourceContext.encodePathSegment(id),
        new QueryParams(),
        RequestQueueRequest.class);
  }

  /**
   * Updates an existing request (identified by its ID field) and returns the operation info. If
   * {@code forefront} is true, the request is moved to the front of the queue.
   */
  public RequestQueueOperationInfo updateRequest(RequestQueueRequest request, boolean forefront) {
    QueryParams params = new QueryParams();
    params.addBool("forefront", forefront);
    withClientKey(params);
    String url =
        ctx.mergedParams(params)
            .applyToUrl(
                ctx.subUrl("requests/" + ResourceContext.encodePathSegment(request.getId())));
    ApiResponse resp =
        http.call(
            "PUT",
            url,
            Json.toBytes(request),
            ResourceContext.CONTENT_TYPE_JSON,
            ResourceContext.DEFAULT_REQUEST_TIMEOUT);
    return Json.parseData(resp.body, RequestQueueOperationInfo.class);
  }

  /** Deletes a request by ID. */
  public void deleteRequest(String id) {
    QueryParams params = withClientKey(new QueryParams());
    String url =
        ctx.mergedParams(params)
            .applyToUrl(ctx.subUrl("requests/" + ResourceContext.encodePathSegment(id)));
    try {
      http.call("DELETE", url, null, "", ResourceContext.DEFAULT_REQUEST_TIMEOUT);
    } catch (ApifyApiException e) {
      if (!ResourceContext.isNotFound(e)) {
        throw e;
      }
    }
  }

  /**
   * Atomically returns and locks up to {@code limit} requests from the head of the queue for {@code
   * lockSecs} seconds. Returns the raw locked-head object.
   */
  public JsonNode listAndLockHead(long lockSecs, Long limit) {
    QueryParams params = new QueryParams();
    params.addLong("lockSecs", lockSecs).addLong("limit", limit);
    withClientKey(params);
    return ctx.postWithBody("head/lock", params, null, "", Json.type(JsonNode.class));
  }

  /**
   * Adds multiple requests to the queue with the default batch options. If {@code forefront} is
   * true, they are added to the front.
   */
  public BatchAddResult batchAddRequests(List<RequestQueueRequest> requests, boolean forefront) {
    return batchAddRequests(requests, forefront, new BatchAddRequestsOptions());
  }

  /**
   * Adds multiple requests to the queue. If {@code forefront} is true, they are added to the front.
   *
   * <p>The input is automatically split into chunks of at most 25 requests (the API limit). Chunks
   * are sent using up to {@link BatchAddRequestsOptions#maxParallel} parallel API calls, and any
   * requests the API leaves unprocessed (typically rate-limited) are retried with exponential
   * backoff up to {@link BatchAddRequestsOptions#maxUnprocessedRequestsRetries} times. The
   * per-chunk results are merged into a single {@link BatchAddResult}.
   */
  public BatchAddResult batchAddRequests(
      List<RequestQueueRequest> requests, boolean forefront, BatchAddRequestsOptions options) {
    List<List<RequestQueueRequest>> chunks = new ArrayList<>();
    for (int start = 0; start < requests.size(); start += MAX_REQUESTS_PER_BATCH) {
      int end = Math.min(start + MAX_REQUESTS_PER_BATCH, requests.size());
      chunks.add(new ArrayList<>(requests.subList(start, end)));
    }

    BatchAddResult merged = new BatchAddResult();
    if (chunks.isEmpty()) {
      return merged;
    }

    int maxParallel = options.maxParallelValue();
    if (maxParallel <= 1 || chunks.size() == 1) {
      for (List<RequestQueueRequest> chunk : chunks) {
        merged.merge(batchAddChunkWithRetries(chunk, forefront, options));
      }
      return merged;
    }

    ExecutorService pool = Executors.newFixedThreadPool(Math.min(maxParallel, chunks.size()));
    try {
      List<Future<BatchAddResult>> futures = new ArrayList<>();
      for (List<RequestQueueRequest> chunk : chunks) {
        futures.add(pool.submit(() -> batchAddChunkWithRetries(chunk, forefront, options)));
      }
      for (Future<BatchAddResult> future : futures) {
        merged.merge(awaitResult(future));
      }
    } finally {
      pool.shutdownNow();
    }
    return merged;
  }

  private static BatchAddResult awaitResult(Future<BatchAddResult> future) {
    try {
      return future.get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new IllegalStateException("batch add request failed", cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HttpClientCore.TransportException(e);
    }
  }

  /**
   * Adds one chunk (already sized to the API limit), retrying requests the API leaves unprocessed
   * with exponential backoff. Never throws for an API error: on a non-retryable failure the
   * remaining requests are returned as unprocessed, matching the reference client's contract.
   */
  private BatchAddResult batchAddChunkWithRetries(
      List<RequestQueueRequest> chunk, boolean forefront, BatchAddRequestsOptions options) {
    int maxRetries = options.maxUnprocessedRequestsRetriesValue();
    long minDelayMillis = options.minDelayBetweenUnprocessedRequestsRetriesMillisValue();

    List<RequestQueueRequest> remaining = chunk;
    List<RequestQueueOperationInfo> processed = new ArrayList<>();

    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        BatchAddResult response = batchAddChunk(remaining, forefront);
        processed.addAll(response.getProcessedRequests());
        remaining = requestsNotYetProcessed(chunk, processed);
        if (remaining.isEmpty()) {
          break;
        }
      } catch (ApifyApiException e) {
        // Transport did not (or was told not to) retry: stop and report everything not yet added as
        // unprocessed so the call keeps its non-throwing signature.
        break;
      }
      if (attempt < maxRetries) {
        sleepBackoff(attempt, minDelayMillis);
      }
    }

    BatchAddResult result = new BatchAddResult();
    result.setProcessedRequests(processed);
    // Unprocessed = everything sent minus everything acknowledged processed. Computing it here
    // (instead of trusting the last response) stays correct even if the API returns fewer entries.
    result.setUnprocessedRequests(requestsNotYetProcessed(chunk, processed));
    return result;
  }

  private static List<RequestQueueRequest> requestsNotYetProcessed(
      List<RequestQueueRequest> chunk, List<RequestQueueOperationInfo> processed) {
    Set<String> processedKeys = new HashSet<>();
    for (RequestQueueOperationInfo info : processed) {
      if (info.getUniqueKey() != null) {
        processedKeys.add(info.getUniqueKey());
      }
    }
    List<RequestQueueRequest> remaining = new ArrayList<>();
    for (RequestQueueRequest request : chunk) {
      if (!processedKeys.contains(request.getUniqueKey())) {
        remaining.add(request);
      }
    }
    return remaining;
  }

  private static void sleepBackoff(int attempt, long minDelayMillis) {
    if (minDelayMillis <= 0) {
      return;
    }
    // (1 + random) * 2^attempt * minDelay — exponential backoff with jitter, matching the
    // reference.
    double factor = (1 + ThreadLocalRandom.current().nextDouble()) * Math.pow(2, attempt);
    long delayMillis = (long) Math.floor(factor * minDelayMillis);
    try {
      Thread.sleep(delayMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HttpClientCore.TransportException(e);
    }
  }

  private BatchAddResult batchAddChunk(List<RequestQueueRequest> requests, boolean forefront) {
    QueryParams params = new QueryParams();
    params.addBool("forefront", forefront);
    withClientKey(params);
    return ctx.postWithBody(
        "requests/batch",
        params,
        Json.toBytes(requests),
        ResourceContext.CONTENT_TYPE_JSON,
        Json.type(BatchAddResult.class));
  }

  /**
   * Deletes multiple requests in a single call. Each entry identifies a request (e.g. by id or
   * uniqueKey). Returns the raw batch result.
   */
  public JsonNode batchDeleteRequests(Object requests) {
    QueryParams params = withClientKey(new QueryParams());
    return ctx.deleteWithBody("requests/batch", params, requests, JsonNode.class);
  }

  /** Lists the queue's requests with pagination. Returns the raw response. */
  public JsonNode listRequests(ListRequestsOptions options) {
    options.validate();
    QueryParams params = new QueryParams();
    options.apply(params);
    withClientKey(params);
    return ctx.getResourceRequired("requests", params, Json.type(JsonNode.class));
  }

  /**
   * Extends the lock on a request by {@code lockSecs} seconds. If {@code forefront} is true, the
   * request is moved to the front when its lock expires. Returns the raw response.
   */
  public JsonNode prolongRequestLock(String id, long lockSecs, boolean forefront) {
    QueryParams params = new QueryParams();
    params.addLong("lockSecs", lockSecs).addBool("forefront", forefront);
    withClientKey(params);
    String url =
        ctx.mergedParams(params)
            .applyToUrl(ctx.subUrl("requests/" + ResourceContext.encodePathSegment(id) + "/lock"));
    ApiResponse resp = http.call("PUT", url, null, "", ResourceContext.DEFAULT_REQUEST_TIMEOUT);
    return Json.parseData(resp.body, Json.type(JsonNode.class));
  }

  /**
   * Releases the lock on a request. If {@code forefront} is true, the request is moved to the front
   * of the queue.
   */
  public void deleteRequestLock(String id, boolean forefront) {
    QueryParams params = new QueryParams();
    params.addBool("forefront", forefront);
    withClientKey(params);
    String url =
        ctx.mergedParams(params)
            .applyToUrl(ctx.subUrl("requests/" + ResourceContext.encodePathSegment(id) + "/lock"));
    try {
      http.call("DELETE", url, null, "", ResourceContext.DEFAULT_REQUEST_TIMEOUT);
    } catch (ApifyApiException e) {
      if (!ResourceContext.isNotFound(e)) {
        throw e;
      }
    }
  }

  /** Releases all locks the client holds on this queue's requests. Returns the raw response. */
  public JsonNode unlockRequests() {
    QueryParams params = withClientKey(new QueryParams());
    return ctx.postWithBody("requests/unlock", params, null, "", Json.type(JsonNode.class));
  }

  /**
   * Returns a lazy iterator over all requests in the queue, fetching pages of up to {@code
   * pageLimit} requests at a time ({@code null} for the server default).
   */
  public Iterator<RequestQueueRequest> paginateRequests(Long pageLimit) {
    return new RequestsIterator(pageLimit);
  }

  /** Shape of a paginated requests listing. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class RequestsPage {
    public List<RequestQueueRequest> items = List.of();
    public String nextCursor;
  }

  /** Lazily iterates over a request queue's requests via the cursor-based listing endpoint. */
  private final class RequestsIterator implements Iterator<RequestQueueRequest> {
    private final Long pageLimit;
    private List<RequestQueueRequest> buffer = List.of();
    private int pos;
    private String nextCursor;
    private boolean started;
    private boolean exhausted;

    RequestsIterator(Long pageLimit) {
      this.pageLimit = pageLimit;
    }

    @Override
    public boolean hasNext() {
      while (pos >= buffer.size()) {
        if (exhausted || (started && (nextCursor == null || nextCursor.isEmpty()))) {
          return false;
        }
        fetchPage();
      }
      return true;
    }

    @Override
    public RequestQueueRequest next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return buffer.get(pos++);
    }

    private void fetchPage() {
      QueryParams params = new QueryParams();
      params.addLong("limit", pageLimit);
      if (nextCursor != null && !nextCursor.isEmpty()) {
        params.addString("cursor", nextCursor);
      }
      withClientKey(params);
      RequestsPage page = ctx.getResourceRequired("requests", params, RequestsPage.class);
      started = true;
      buffer = page.items;
      pos = 0;
      nextCursor = page.nextCursor;
      if (page.items.isEmpty() && (nextCursor == null || nextCursor.isEmpty())) {
        exhausted = true;
      }
    }
  }
}
