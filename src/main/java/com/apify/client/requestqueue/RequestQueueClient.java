package com.apify.client.requestqueue;

import com.apify.client.http.ApifyApiException;
import com.apify.client.http.ApifyTransportException;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.Json;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
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

  /**
   * The API's request-body payload-size limit for a batch-add call, in bytes (9 MiB), matching the
   * reference JS client's {@code MAX_PAYLOAD_SIZE_BYTES}. A batch of up to {@link
   * #MAX_REQUESTS_PER_BATCH} requests can still exceed this if the requests are individually large
   * (e.g. sizeable {@code userData}), so {@link #batchAddRequests(List, boolean,
   * BatchAddRequestsOptions)} additionally splits chunks by cumulative JSON-encoded byte size to
   * avoid a 413 (Payload Too Large).
   */
  private static final long MAX_PAYLOAD_SIZE_BYTES = 9L * 1024 * 1024;

  /**
   * Safety margin subtracted from {@link #MAX_PAYLOAD_SIZE_BYTES} before slicing by byte size, so a
   * chunk sized right at the boundary is not rejected due to minor framing overhead. Matches the
   * reference client's {@code SAFETY_BUFFER_PERCENT}.
   */
  private static final double PAYLOAD_SAFETY_BUFFER_PERCENT = 0.01 / 100;

  /** The two bytes of an empty JSON array ({@code []}), the baseline for a byte-size chunk. */
  private static final int EMPTY_JSON_ARRAY_BYTES = 2;

  /**
   * Upper bound on the unprocessed-requests backoff exponent, so {@code 2^attempt} cannot blow up.
   */
  private static final int MAX_BACKOFF_EXPONENT = 10;

  private final HttpClientCore http;
  private final ResourceContext ctx;
  private final String clientKey;

  public RequestQueueClient(HttpClientCore http, String baseUrl, String id) {
    this(http, ResourceContext.single(http, baseUrl, ApiPaths.REQUEST_QUEUES, id), null);
  }

  private RequestQueueClient(HttpClientCore http, ResourceContext ctx, String clientKey) {
    this.http = http;
    this.ctx = ctx;
    this.clientKey = clientKey;
  }

  /** Creates a client for a run's default request queue (nested path only, no ID). */
  public static RequestQueueClient nested(HttpClientCore http, String base, String subPath) {
    return nested(http, base, subPath, null);
  }

  /** As {@link #nested(HttpClientCore, String, String)} but inheriting parent query params. */
  public static RequestQueueClient nested(
      HttpClientCore http, String base, String subPath, QueryParams inherited) {
    return new RequestQueueClient(
        http, ResourceContext.collection(http, base, subPath).seedParams(inherited), null);
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
    return ctx.putWithBody(
        "requests/" + ResourceContext.encodePathSegment(request.getId()),
        params,
        Json.toBytes(request),
        ResourceContext.CONTENT_TYPE_JSON,
        RequestQueueOperationInfo.class);
  }

  /** Deletes a request by ID. */
  public void deleteRequest(String id) {
    QueryParams params = withClientKey(new QueryParams());
    String url =
        ctx.mergedParams(params)
            .applyToUrl(ctx.subUrl("requests/" + ResourceContext.encodePathSegment(id)));
    try {
      http.call("DELETE", url, null, "", http.baseRequestTimeout());
    } catch (ApifyApiException e) {
      if (!ResourceContext.isNotFound(e)) {
        throw e;
      }
    }
  }

  /**
   * Atomically returns and locks up to {@code limit} requests from the head of the queue for {@code
   * lockSecs} seconds, so other clients cannot retrieve them until the lock expires or is released
   * via {@link #deleteRequestLock}. This is the primary method used by distributed crawlers to
   * coordinate work across multiple workers.
   */
  public LockedRequestQueueHead listAndLockHead(long lockSecs, Long limit) {
    QueryParams params = new QueryParams();
    params.addLong("lockSecs", lockSecs).addLong("limit", limit);
    withClientKey(params);
    return ctx.postWithBody("head/lock", params, null, "", LockedRequestQueueHead.class);
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
   * <p>The input is automatically split into chunks of at most 25 requests (the API count limit)
   * that additionally respect the API's ~9 MiB request-body payload-size limit ({@link
   * #MAX_PAYLOAD_SIZE_BYTES}): a chunk of up to 25 requests is further split by cumulative
   * JSON-encoded byte size, so a batch of individually large requests (e.g. sizeable {@code
   * userData}) cannot 413. Chunks are sent using up to {@link BatchAddRequestsOptions#maxParallel}
   * parallel API calls, and any requests the API leaves unprocessed (typically rate-limited) are
   * retried with exponential backoff up to {@link
   * BatchAddRequestsOptions#maxUnprocessedRequestsRetries} times. The per-chunk results are merged
   * into a single {@link BatchAddResult}.
   *
   * <p>This method never throws for an API error, matching the reference client's contract: any
   * request that could not be confirmed processed — whether due to persistent rate-limiting, server
   * errors, or a non-retryable client error (e.g. an invalid token or insufficient permissions) —
   * is returned in {@link BatchAddResult#getUnprocessedRequests()} instead. A single request whose
   * own JSON encoding already exceeds the payload-size limit is rejected up front with {@link
   * IllegalArgumentException}, since no chunk size could ever fit it.
   *
   * <p><b>Caveat:</b> processed-vs-unprocessed reconciliation after a retry matches requests by
   * {@link RequestQueueRequest#getUniqueKey()}. If a request omits {@code uniqueKey} (it is
   * nullable), a request that actually succeeded server-side on an earlier attempt can still be
   * reported in {@link BatchAddResult#getUnprocessedRequests()} after retries, because there is no
   * caller-supplied key to match it back against the API's per-attempt response. Callers that rely
   * on {@code batchAddRequests}' return value should set {@code uniqueKey} explicitly.
   */
  public BatchAddResult batchAddRequests(
      List<RequestQueueRequest> requests, boolean forefront, BatchAddRequestsOptions options) {
    long payloadSizeLimitBytes =
        MAX_PAYLOAD_SIZE_BYTES
            - (long) Math.ceil(MAX_PAYLOAD_SIZE_BYTES * PAYLOAD_SAFETY_BUFFER_PERCENT);

    List<List<RequestQueueRequest>> chunks = new ArrayList<>();
    for (int start = 0; start < requests.size(); ) {
      int end = Math.min(start + MAX_REQUESTS_PER_BATCH, requests.size());
      List<RequestQueueRequest> countSlice = requests.subList(start, end);
      List<RequestQueueRequest> chunk = sliceByByteLength(countSlice, payloadSizeLimitBytes, start);
      chunks.add(new ArrayList<>(chunk));
      start += chunk.size();
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

  /**
   * Returns the longest leading run of {@code requests} whose combined JSON payload stays under
   * {@code maxByteLength}, always keeping at least one request so the caller's chunking loop makes
   * forward progress. Ports the reference client's {@code sliceArrayByByteLength}. {@code
   * startIndex} is the position of {@code requests.get(0)} in the caller's full input list, used
   * only to report which request is oversized.
   *
   * @throws IllegalArgumentException if any individual request's own JSON encoding already exceeds
   *     {@code maxByteLength} (no chunk size could ever fit it)
   */
  private static List<RequestQueueRequest> sliceByByteLength(
      List<RequestQueueRequest> requests, long maxByteLength, int startIndex) {
    if (jsonByteLength(requests) < maxByteLength) {
      return requests;
    }

    List<RequestQueueRequest> sliced = new ArrayList<>();
    long byteLength = EMPTY_JSON_ARRAY_BYTES;
    for (int i = 0; i < requests.size(); i++) {
      RequestQueueRequest request = requests.get(i);
      long itemBytes = jsonByteLength(request);
      if (itemBytes > maxByteLength) {
        throw new IllegalArgumentException(
            "RequestQueueClient.batchAddRequests: the request at index "
                + (startIndex + i)
                + " exceeds the maximum allowed payload size ("
                + maxByteLength
                + " bytes)");
      }
      if (byteLength + itemBytes >= maxByteLength) {
        break;
      }
      byteLength += itemBytes;
      sliced.add(request);
    }

    // Guarantee forward progress: keep at least the first request. It cannot be individually
    // oversized (that would already have thrown above), so this never produces an over-limit chunk.
    if (sliced.isEmpty()) {
      sliced.add(requests.get(0));
    }
    return sliced;
  }

  /** The byte length of {@code value}'s JSON encoding, as sent on the wire. */
  private static long jsonByteLength(Object value) {
    return Json.toBytes(value).length;
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
      throw new ApifyTransportException(e);
    }
  }

  /**
   * Adds one chunk (already sized to the API limit), retrying requests the API leaves unprocessed
   * with exponential backoff. Never throws for an API error, matching the reference client's {@code
   * _batchAddRequestsWithRetries}: on any failure (including a non-retryable 4xx) the remaining
   * requests in the chunk are simply returned as unprocessed rather than surfaced as an exception,
   * so the method keeps a single, uniform never-throws contract regardless of the failure cause.
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
      } catch (ApifyApiException ignored) {
        // Any API error (rate-limit, server error, or a hard client error such as a bad token)
        // stops retrying this chunk immediately; whatever has not been confirmed processed is
        // reported as unprocessed below, never thrown — matching the reference client's contract.
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
    // reference. The exponent is capped so a pathologically large retry count cannot overflow the
    // delay into an absurd (or negative, after the long cast) sleep.
    int cappedAttempt = Math.min(attempt, MAX_BACKOFF_EXPONENT);
    double factor = (1 + ThreadLocalRandom.current().nextDouble()) * Math.pow(2, cappedAttempt);
    long delayMillis = (long) Math.floor(factor * minDelayMillis);
    try {
      Thread.sleep(delayMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApifyTransportException(e);
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
   * uniqueKey).
   */
  public BatchDeleteResult batchDeleteRequests(Object requests) {
    QueryParams params = withClientKey(new QueryParams());
    return ctx.deleteWithBody("requests/batch", params, requests, BatchDeleteResult.class);
  }

  /** Lists the queue's requests with pagination. */
  public RequestsList listRequests(ListRequestsOptions options) {
    options.validate();
    QueryParams params = new QueryParams();
    options.apply(params);
    withClientKey(params);
    return ctx.getResourceRequired("requests", params, RequestsList.class);
  }

  /**
   * Extends the lock on a request by {@code lockSecs} seconds. If {@code forefront} is true, the
   * request is moved to the front when its lock expires.
   */
  public RequestLockInfo prolongRequestLock(String id, long lockSecs, boolean forefront) {
    QueryParams params = new QueryParams();
    params.addLong("lockSecs", lockSecs).addBool("forefront", forefront);
    withClientKey(params);
    return ctx.putWithBody(
        "requests/" + ResourceContext.encodePathSegment(id) + "/lock",
        params,
        null,
        "",
        RequestLockInfo.class);
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
      http.call("DELETE", url, null, "", http.baseRequestTimeout());
    } catch (ApifyApiException e) {
      if (!ResourceContext.isNotFound(e)) {
        throw e;
      }
    }
  }

  /** Releases all locks the client holds on this queue's requests. */
  public UnlockRequestsResult unlockRequests() {
    QueryParams params = withClientKey(new QueryParams());
    return ctx.postWithBody("requests/unlock", params, null, "", UnlockRequestsResult.class);
  }

  /**
   * Returns a lazy iterator over all requests in the queue, fetching pages of up to {@code
   * pageLimit} requests at a time ({@code null} for the server default). Equivalent to {@link
   * #paginateRequests(Long, Long, List) paginateRequests(null, pageLimit, null)} (no total cap, no
   * state filter).
   */
  public Iterator<RequestQueueRequest> paginateRequests(Long pageLimit) {
    return paginateRequests(null, pageLimit, null);
  }

  /**
   * Returns a lazy iterator over the queue's requests via the cursor-based listing endpoint. {@code
   * totalLimit} caps the total number of requests yielded across all pages ({@code
   * null}/non-positive = unbounded); {@code chunkSize} is the per-request page size ({@code null} =
   * server default); {@code filter} restricts to requests in the given states ({@link
   * ListRequestsOptions#FILTER_LOCKED}/{@link ListRequestsOptions#FILTER_PENDING}, {@code null} =
   * no filter), matching {@link #listRequests(ListRequestsOptions)}'s filter.
   *
   * <p>Always starts from the beginning of the queue; resuming from an explicit {@code
   * exclusiveStartId}/{@code cursor} is not supported here (use {@link
   * #listRequests(ListRequestsOptions)} directly for that single-page use case).
   */
  public Iterator<RequestQueueRequest> paginateRequests(
      Long totalLimit, Long chunkSize, List<String> filter) {
    return new RequestsIterator(totalLimit, chunkSize, filter);
  }

  /** Lazily iterates over a request queue's requests via the cursor-based listing endpoint. */
  private final class RequestsIterator implements Iterator<RequestQueueRequest> {
    private final Long totalLimit;
    private final Long chunkSize;
    private final List<String> filter;
    private List<RequestQueueRequest> buffer = List.of();
    private int pos;
    private String nextCursor;
    private long yielded;
    private boolean started;
    private boolean exhausted;

    RequestsIterator(Long totalLimit, Long chunkSize, List<String> filter) {
      this.totalLimit = totalLimit != null && totalLimit > 0 ? totalLimit : null;
      this.chunkSize = chunkSize;
      this.filter = filter;
    }

    @Override
    public boolean hasNext() {
      while (pos >= buffer.size()) {
        if (exhausted || (started && (nextCursor == null || nextCursor.isEmpty()))) {
          return false;
        }
        if (totalLimit != null && yielded >= totalLimit) {
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
      yielded++;
      return buffer.get(pos++);
    }

    private void fetchPage() {
      QueryParams params = new QueryParams();
      Long capRemaining = totalLimit != null ? totalLimit - yielded : null;
      Long pageLimit =
          capRemaining == null
              ? chunkSize
              : (chunkSize == null ? capRemaining : Math.min(capRemaining, chunkSize));
      params.addLong("limit", pageLimit);
      if (nextCursor != null && !nextCursor.isEmpty()) {
        params.addString("cursor", nextCursor);
      }
      params.addCsv("filter", filter);
      withClientKey(params);
      RequestsList page = ctx.getResourceRequired("requests", params, RequestsList.class);
      started = true;
      buffer = page.getItems();
      pos = 0;
      nextCursor = page.getNextCursor();
      // Defensively trim to the cap in case the server returned more than requested, matching
      // PaginatedIterator's same guard, so a caller never sees more than `totalLimit` items even
      // if the server ignores (or overshoots) the requested per-page `limit`.
      if (capRemaining != null && buffer.size() > capRemaining) {
        buffer = buffer.subList(0, capRemaining.intValue());
      }
      if (buffer.isEmpty() && (nextCursor == null || nextCursor.isEmpty())) {
        exhausted = true;
      }
    }
  }
}
