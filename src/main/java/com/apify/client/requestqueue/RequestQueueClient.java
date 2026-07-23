package com.apify.client.requestqueue;

import com.apify.client.http.ApifyApiException;
import com.apify.client.http.ApifyTransportException;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.Async;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.Json;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
        http, ResourceContext.nestedCollection(http, base, subPath, inherited), null);
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
  public CompletableFuture<Optional<RequestQueue>> get() {
    return ctx.getResource("", new QueryParams(), RequestQueue.class);
  }

  /** Updates the queue metadata (e.g. name) and returns the updated object. */
  public CompletableFuture<RequestQueue> update(Object newFields) {
    return ctx.updateResource("", newFields, RequestQueue.class);
  }

  /** Deletes the queue. */
  public CompletableFuture<Void> delete() {
    return ctx.deleteResource("");
  }

  /**
   * Returns the requests at the head (front) of the queue, up to {@code limit} ({@code null} for
   * the server default).
   */
  public CompletableFuture<RequestQueueHead> listHead(Long limit) {
    QueryParams params = new QueryParams();
    params.addLong("limit", limit);
    withClientKey(params);
    return ctx.getResourceRequired("head", params, RequestQueueHead.class);
  }

  /** Adds a request to the queue. If {@code forefront} is true, it is added to the front. */
  public CompletableFuture<RequestQueueOperationInfo> addRequest(
      RequestQueueRequest request, boolean forefront) {
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
  public CompletableFuture<Optional<RequestQueueRequest>> getRequest(String id) {
    return ctx.getResource(
        "requests/" + ResourceContext.encodePathSegment(id),
        new QueryParams(),
        RequestQueueRequest.class);
  }

  /**
   * Updates an existing request (identified by its ID field) and returns the operation info. If
   * {@code forefront} is true, the request is moved to the front of the queue.
   */
  public CompletableFuture<RequestQueueOperationInfo> updateRequest(
      RequestQueueRequest request, boolean forefront) {
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
  public CompletableFuture<Void> deleteRequest(String id) {
    QueryParams params = withClientKey(new QueryParams());
    String url =
        ctx.mergedParams(params)
            .applyToUrl(ctx.subUrl("requests/" + ResourceContext.encodePathSegment(id)));
    return http.call("DELETE", url, null, "", http.baseRequestTimeout())
        .handle(
            (resp, error) -> {
              if (error == null) {
                return null;
              }
              Throwable cause = HttpClientCore.unwrapCompletion(error);
              if (cause instanceof ApifyApiException apiError
                  && ResourceContext.isNotFound(apiError)) {
                return null;
              }
              throw rethrow(cause);
            });
  }

  /**
   * Atomically returns and locks up to {@code limit} requests from the head of the queue for {@code
   * lockSecs} seconds, so other clients cannot retrieve them until the lock expires or is released
   * via {@link #deleteRequestLock}. This is the primary method used by distributed crawlers to
   * coordinate work across multiple workers.
   */
  public CompletableFuture<LockedRequestQueueHead> listAndLockHead(long lockSecs, Long limit) {
    QueryParams params = new QueryParams();
    params.addLong("lockSecs", lockSecs).addLong("limit", limit);
    withClientKey(params);
    return ctx.postWithBody("head/lock", params, null, "", LockedRequestQueueHead.class);
  }

  /**
   * Adds multiple requests to the queue with the default batch options. If {@code forefront} is
   * true, they are added to the front.
   */
  public CompletableFuture<BatchAddResult> batchAddRequests(
      List<RequestQueueRequest> requests, boolean forefront) {
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
   * concurrently in-flight chunk requests, and any requests the API leaves unprocessed (typically
   * rate-limited) are retried with exponential backoff up to {@link
   * BatchAddRequestsOptions#maxUnprocessedRequestsRetries} times. The per-chunk results are merged
   * into a single {@link BatchAddResult}.
   *
   * <p>This method's returned future never completes exceptionally due to an API/transport failure,
   * matching the reference client's contract: any request that could not be confirmed processed —
   * whether due to persistent rate-limiting, server errors, or a non-retryable client error (e.g.
   * an invalid token or insufficient permissions) — is returned in {@link
   * BatchAddResult#getUnprocessedRequests()} instead. A single request whose own JSON encoding
   * already exceeds the payload-size limit is rejected up front with {@link
   * IllegalArgumentException}, since no chunk size could ever fit it.
   *
   * <p><b>Caveat:</b> processed-vs-unprocessed reconciliation after a retry matches requests by
   * {@link RequestQueueRequest#getUniqueKey()}. If a request omits {@code uniqueKey} (it is
   * nullable), a request that actually succeeded server-side on an earlier attempt can still be
   * reported in {@link BatchAddResult#getUnprocessedRequests()} after retries, because there is no
   * caller-supplied key to match it back against the API's per-attempt response. Callers that rely
   * on {@code batchAddRequests}' return value should set {@code uniqueKey} explicitly.
   */
  public CompletableFuture<BatchAddResult> batchAddRequests(
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

    if (chunks.isEmpty()) {
      return CompletableFuture.completedFuture(new BatchAddResult());
    }

    int maxParallel = Math.max(1, options.maxParallelValue());
    int workerCount = Math.min(maxParallel, chunks.size());
    BatchAddResult[] resultsByChunk = new BatchAddResult[chunks.size()];
    AtomicInteger nextChunkIndex = new AtomicInteger(0);

    List<CompletableFuture<Void>> workers = new ArrayList<>(workerCount);
    for (int w = 0; w < workerCount; w++) {
      workers.add(runBatchAddWorker(chunks, nextChunkIndex, resultsByChunk, forefront, options));
    }
    return CompletableFuture.allOf(workers.toArray(CompletableFuture[]::new))
        .thenApply(
            unused -> {
              BatchAddResult merged = new BatchAddResult();
              for (BatchAddResult chunkResult : resultsByChunk) {
                merged.merge(chunkResult);
              }
              return merged;
            });
  }

  /**
   * One "worker": repeatedly claims the next not-yet-started chunk (via {@code nextChunkIndex}) and
   * processes it, until every chunk has been claimed. Running {@code workerCount} of these
   * concurrently bounds how many chunk requests (including their own retries) are in flight at
   * once, matching {@link BatchAddRequestsOptions#maxParallel} without needing a thread pool - each
   * worker is a chain of async continuations, not a blocked thread.
   */
  private CompletableFuture<Void> runBatchAddWorker(
      List<List<RequestQueueRequest>> chunks,
      AtomicInteger nextChunkIndex,
      BatchAddResult[] resultsByChunk,
      boolean forefront,
      BatchAddRequestsOptions options) {
    int index = nextChunkIndex.getAndIncrement();
    if (index >= chunks.size()) {
      return CompletableFuture.completedFuture(null);
    }
    return batchAddChunkWithRetries(chunks.get(index), forefront, options)
        .thenCompose(
            result -> {
              resultsByChunk[index] = result;
              return runBatchAddWorker(chunks, nextChunkIndex, resultsByChunk, forefront, options);
            });
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
      // Every element but the first is preceded by a comma in the serialized array (`[a,b,c]` has
      // count-1 commas); account for it here so this incremental estimate agrees exactly with
      // jsonByteLength(requests)'s full-array measurement above, rather than relying on being
      // slightly under it.
      long commaBytes = sliced.isEmpty() ? 0 : 1;
      if (byteLength + commaBytes + itemBytes >= maxByteLength) {
        break;
      }
      byteLength += commaBytes + itemBytes;
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

  /**
   * Adds one chunk (already sized to the API limit), retrying requests the API leaves unprocessed
   * with exponential backoff (a scheduled delay, not a blocked thread; see {@link Async}). The
   * returned future never completes exceptionally, matching the reference client's {@code
   * _batchAddRequestsWithRetries}: on any failure — a non-retryable 4xx/5xx API response, or a
   * transport-level failure (connection error, timeout) that never produced a response at all — the
   * remaining requests in the chunk are simply reported as unprocessed rather than surfaced as an
   * exception, so the method keeps a single, uniform never-fails contract across both {@link
   * ApifyApiException} and {@link com.apify.client.http.ApifyTransportException}.
   */
  private CompletableFuture<BatchAddResult> batchAddChunkWithRetries(
      List<RequestQueueRequest> chunk, boolean forefront, BatchAddRequestsOptions options) {
    int maxRetries = options.maxUnprocessedRequestsRetriesValue();
    long minDelayMillis = options.minDelayBetweenUnprocessedRequestsRetriesMillisValue();
    return batchAddAttempt(chunk, chunk, List.of(), forefront, 0, maxRetries, minDelayMillis);
  }

  /** One attempt of the retry loop backing {@link #batchAddChunkWithRetries}. */
  private CompletableFuture<BatchAddResult> batchAddAttempt(
      List<RequestQueueRequest> chunk,
      List<RequestQueueRequest> toSend,
      List<RequestQueueOperationInfo> processedSoFar,
      boolean forefront,
      int attempt,
      int maxRetries,
      long minDelayMillis) {
    return batchAddChunk(toSend, forefront)
        .handle(
            (response, error) -> {
              if (error == null) {
                return response;
              }
              Throwable cause = HttpClientCore.unwrapCompletion(error);
              if (cause instanceof ApifyApiException || cause instanceof ApifyTransportException) {
                // A non-retryable API error or a transport failure: stop retrying this chunk, with
                // whatever was already confirmed processed. Any other (unexpected) exception is
                // rethrown rather than swallowed.
                return null;
              }
              throw rethrow(cause);
            })
        .thenCompose(
            response -> {
              List<RequestQueueOperationInfo> processed = new ArrayList<>(processedSoFar);
              if (response != null) {
                processed.addAll(response.getProcessedRequests());
              }
              List<RequestQueueRequest> stillRemaining = requestsNotYetProcessed(chunk, processed);
              // Any client-level failure (response == null, i.e. the batchAddChunk call above
              // failed) stops retrying this chunk immediately; whatever has not been confirmed
              // processed is reported as unprocessed below, never thrown - matching the reference
              // client's contract.
              boolean stop = response == null || stillRemaining.isEmpty() || attempt >= maxRetries;
              if (stop) {
                return CompletableFuture.completedFuture(finish(chunk, processed));
              }
              return Async.delay(backoffDelay(attempt, minDelayMillis))
                  .thenCompose(
                      unused ->
                          batchAddAttempt(
                              chunk,
                              stillRemaining,
                              processed,
                              forefront,
                              attempt + 1,
                              maxRetries,
                              minDelayMillis));
            })
        .exceptionally(error -> finish(chunk, processedSoFar));
  }

  private static BatchAddResult finish(
      List<RequestQueueRequest> chunk, List<RequestQueueOperationInfo> processed) {
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

  /**
   * {@code (1 + random) * 2^attempt * minDelay} — exponential backoff with jitter, matching the
   * reference. The exponent is capped so a pathologically large retry count cannot overflow the
   * delay into an absurd (or negative) duration.
   */
  private static Duration backoffDelay(int attempt, long minDelayMillis) {
    if (minDelayMillis <= 0) {
      return Duration.ZERO;
    }
    int cappedAttempt = Math.min(attempt, MAX_BACKOFF_EXPONENT);
    double factor = (1 + ThreadLocalRandom.current().nextDouble()) * Math.pow(2, cappedAttempt);
    return Duration.ofMillis((long) Math.floor(factor * minDelayMillis));
  }

  private CompletableFuture<BatchAddResult> batchAddChunk(
      List<RequestQueueRequest> requests, boolean forefront) {
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
  public CompletableFuture<BatchDeleteResult> batchDeleteRequests(Object requests) {
    QueryParams params = withClientKey(new QueryParams());
    return ctx.deleteWithBody("requests/batch", params, requests, BatchDeleteResult.class);
  }

  /** Lists the queue's requests with pagination. */
  public CompletableFuture<RequestsList> listRequests(ListRequestsOptions options) {
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
  public CompletableFuture<RequestLockInfo> prolongRequestLock(
      String id, long lockSecs, boolean forefront) {
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
  public CompletableFuture<Void> deleteRequestLock(String id, boolean forefront) {
    QueryParams params = new QueryParams();
    params.addBool("forefront", forefront);
    withClientKey(params);
    String url =
        ctx.mergedParams(params)
            .applyToUrl(ctx.subUrl("requests/" + ResourceContext.encodePathSegment(id) + "/lock"));
    return http.call("DELETE", url, null, "", http.baseRequestTimeout())
        .handle(
            (resp, error) -> {
              if (error == null) {
                return null;
              }
              Throwable cause = HttpClientCore.unwrapCompletion(error);
              if (cause instanceof ApifyApiException apiError
                  && ResourceContext.isNotFound(apiError)) {
                return null;
              }
              throw rethrow(cause);
            });
  }

  /** Releases all locks the client holds on this queue's requests. */
  public CompletableFuture<UnlockRequestsResult> unlockRequests() {
    QueryParams params = withClientKey(new QueryParams());
    return ctx.postWithBody("requests/unlock", params, null, "", UnlockRequestsResult.class);
  }

  /**
   * Returns a lazy, backpressure-aware publisher over all requests in the queue, fetching pages of
   * up to {@code pageLimit} requests at a time ({@code null} for the server default). Equivalent to
   * {@link #paginateRequests(Long, Long, List) paginateRequests(null, pageLimit, null)} (no total
   * cap, no state filter).
   */
  public Flow.Publisher<RequestQueueRequest> paginateRequests(Long pageLimit) {
    return paginateRequests(null, pageLimit, null);
  }

  /**
   * Returns a lazy, backpressure-aware publisher over the queue's requests via the cursor-based
   * listing endpoint. {@code totalLimit} caps the total number of requests yielded across all pages
   * ({@code null}/non-positive = unbounded); {@code chunkSize} is the per-request page size ({@code
   * null} = server default); {@code filter} restricts to requests in the given states ({@link
   * ListRequestsOptions#FILTER_LOCKED}/{@link ListRequestsOptions#FILTER_PENDING}, {@code null} =
   * no filter), matching {@link #listRequests(ListRequestsOptions)}'s filter.
   *
   * <p>Always starts from the beginning of the queue; resuming from an explicit {@code
   * exclusiveStartId}/{@code cursor} is not supported here (use {@link
   * #listRequests(ListRequestsOptions)} directly for that single-page use case).
   */
  public Flow.Publisher<RequestQueueRequest> paginateRequests(
      Long totalLimit, Long chunkSize, List<String> filter) {
    return new RequestsPublisher(totalLimit, chunkSize, filter);
  }

  /**
   * Lazily publishes a request queue's requests via the cursor-based listing endpoint, fetching a
   * page only once the subscriber has signalled demand. Supports a single subscriber, like {@link
   * com.apify.client.internal.AsyncPaginatedPublisher}, whose draining design this mirrors (cursor
   * pagination instead of offset/limit, plus the "not yet started" distinction the cursor-based
   * termination check needs, are the only real differences).
   */
  private final class RequestsPublisher implements Flow.Publisher<RequestQueueRequest> {
    private final Long totalLimit;
    private final Long chunkSize;
    private final List<String> filter;
    private final AtomicBoolean subscribed = new AtomicBoolean();

    RequestsPublisher(Long totalLimit, Long chunkSize, List<String> filter) {
      this.totalLimit = totalLimit != null && totalLimit > 0 ? totalLimit : null;
      this.chunkSize = chunkSize;
      this.filter = filter;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super RequestQueueRequest> subscriber) {
      if (!subscribed.compareAndSet(false, true)) {
        subscriber.onSubscribe(
            new Flow.Subscription() {
              @Override
              public void request(long n) {}

              @Override
              public void cancel() {}
            });
        subscriber.onError(
            new IllegalStateException(
                "This publisher supports only a single subscriber (already subscribed)"));
        return;
      }
      subscriber.onSubscribe(new Session(subscriber));
    }

    private final class Session implements Flow.Subscription {
      private final Flow.Subscriber<? super RequestQueueRequest> subscriber;
      private final AtomicLong requested = new AtomicLong();
      private final AtomicBoolean draining = new AtomicBoolean();
      private final AtomicBoolean cancelled = new AtomicBoolean();
      private final AtomicBoolean terminated = new AtomicBoolean();

      // Only touched from within the draining-guarded section; see AsyncPaginatedPublisher.Session.
      private List<RequestQueueRequest> buffer = List.of();
      private int pos;
      private String nextCursor;
      private long yielded;
      private boolean started;
      private boolean exhausted;

      Session(Flow.Subscriber<? super RequestQueueRequest> subscriber) {
        this.subscriber = subscriber;
      }

      @Override
      public void request(long n) {
        if (n <= 0) {
          if (terminated.compareAndSet(false, true)) {
            subscriber.onError(
                new IllegalArgumentException(
                    "Reactive Streams violation: requested amount must be positive, was " + n));
          }
          return;
        }
        requested.updateAndGet(current -> current + n < 0 ? Long.MAX_VALUE : current + n);
        drain();
      }

      @Override
      public void cancel() {
        cancelled.set(true);
      }

      private void drain() {
        if (draining.compareAndSet(false, true)) {
          drainLoop();
        }
      }

      private boolean isExhausted() {
        return exhausted
            || (started && (nextCursor == null || nextCursor.isEmpty()))
            || (totalLimit != null && yielded >= totalLimit);
      }

      private void drainLoop() {
        while (!cancelled.get() && requested.get() > 0 && pos < buffer.size()) {
          RequestQueueRequest item = buffer.get(pos++);
          yielded++;
          requested.decrementAndGet();
          subscriber.onNext(item);
        }
        if (cancelled.get()) {
          draining.set(false);
          return;
        }
        if (pos < buffer.size()) {
          draining.set(false);
          if (requested.get() > 0 && pos < buffer.size()) {
            drain();
          }
          return;
        }
        if (isExhausted()) {
          if (terminated.compareAndSet(false, true)) {
            subscriber.onComplete();
          }
          draining.set(false);
          return;
        }
        fetchPage();
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
        ctx.getResourceRequired("requests", params, RequestsList.class)
            .whenComplete(
                (page, error) -> {
                  if (cancelled.get()) {
                    draining.set(false);
                    return;
                  }
                  if (error != null) {
                    if (terminated.compareAndSet(false, true)) {
                      subscriber.onError(HttpClientCore.unwrapCompletion(error));
                    }
                    draining.set(false);
                    return;
                  }
                  applyPage(page, capRemaining);
                  drainLoop();
                });
      }

      private void applyPage(RequestsList page, Long capRemaining) {
        started = true;
        buffer = page.getItems();
        pos = 0;
        nextCursor = page.getNextCursor();
        // Defensively trim to the cap in case the server returned more than requested, matching
        // AsyncPaginatedPublisher's same guard, so a subscriber never sees more than `totalLimit`
        // items even if the server ignores (or overshoots) the requested per-page `limit`.
        if (capRemaining != null && buffer.size() > capRemaining) {
          buffer = buffer.subList(0, capRemaining.intValue());
        }
        if (buffer.isEmpty() && (nextCursor == null || nextCursor.isEmpty())) {
          exhausted = true;
        }
      }
    }
  }

  /** Rethrows a classified cause as an unchecked exception, as-is (defensive fallback only). */
  private static RuntimeException rethrow(Throwable cause) {
    if (cause instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new ApifyTransportException(cause);
  }
}
