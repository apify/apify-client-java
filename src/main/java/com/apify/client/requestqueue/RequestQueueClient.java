package com.apify.client.requestqueue;

import com.apify.client.http.ApifyApiException;
import com.apify.client.http.ApifyTransportException;
import com.apify.client.http.Timeout;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.Async;
import com.apify.client.internal.CancellableFuture;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.Json;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.io.ByteArrayOutputStream;
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

  /** The API's payload-size limit for a batch-add call, in bytes (9 MiB). */
  private static final long MAX_PAYLOAD_SIZE_BYTES = 9L * 1024 * 1024;

  /** Fraction of {@link #MAX_PAYLOAD_SIZE_BYTES} kept free for framing overhead. */
  private static final double PAYLOAD_SAFETY_BUFFER_PERCENT = 0.01 / 100;

  /** The two bytes of an empty JSON array ({@code []}), the baseline for a byte-size chunk. */
  private static final int EMPTY_JSON_ARRAY_BYTES = 2;

  /** Upper bound on the backoff exponent for unprocessed requests. */
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

  /**
   * Fetches the queue metadata, or empty if it does not exist.
   *
   * <p>On a client reached through a run/task without an explicit queue id (e.g. {@code
   * run.requestQueue()}), a 404 is ambiguous between "the run is gone" and "the run has no queue",
   * so it throws {@link com.apify.client.http.NotFoundError} instead of resolving to empty.
   */
  public CompletableFuture<Optional<RequestQueue>> get() {
    return ctx.getResourceUnlessAmbiguous("", new QueryParams(), RequestQueue.class);
  }

  /** Updates the queue metadata (e.g. name) and returns the updated object. */
  public CompletableFuture<RequestQueue> update(Object newFields) {
    return ctx.updateResource("", newFields, RequestQueue.class);
  }

  /**
   * Deletes the queue. See {@link #get()} for why a client without an explicit queue id throws on a
   * 404 instead of treating it as a no-op.
   */
  public CompletableFuture<Void> delete() {
    return ctx.deleteResourceUnlessAmbiguous("");
  }

  /**
   * Returns the requests at the head (front) of the queue, up to {@code limit} ({@code null} for
   * the server default).
   */
  public CompletableFuture<RequestQueueHead> listHead(Long limit) {
    QueryParams params = new QueryParams();
    params.addLong("limit", limit);
    withClientKey(params);
    return ctx.getResourceRequired("head", params, RequestQueueHead.class, Timeout.SHORT);
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
        RequestQueueOperationInfo.class,
        Timeout.SHORT);
  }

  /** Fetches a request by ID, or empty if it does not exist. */
  public CompletableFuture<Optional<RequestQueueRequest>> getRequest(String id) {
    return ctx.getResource(
        "requests/" + ResourceContext.encodePathSegment(id),
        new QueryParams(),
        RequestQueueRequest.class,
        Timeout.SHORT);
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
        RequestQueueOperationInfo.class,
        Timeout.MEDIUM);
  }

  /** Deletes a request by ID. */
  public CompletableFuture<Void> deleteRequest(String id) {
    QueryParams params = withClientKey(new QueryParams());
    String url =
        ctx.mergedParams(params)
            .applyToUrl(ctx.subUrl("requests/" + ResourceContext.encodePathSegment(id)));
    return http.call("DELETE", url, null, "", Timeout.SHORT)
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
    return ctx.postWithBody(
        "head/lock", params, null, "", LockedRequestQueueHead.class, Timeout.MEDIUM);
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
   * <p>The input is split into chunks of at most 25 requests (the API limit) and of at most {@link
   * #MAX_PAYLOAD_SIZE_BYTES} of JSON, with up to {@link BatchAddRequestsOptions#maxParallel} chunks
   * in flight. Requests the API leaves unprocessed are retried with exponential backoff up to
   * {@link BatchAddRequestsOptions#maxUnprocessedRequestsRetries} times. The chunk results are
   * merged into one {@link BatchAddResult}.
   *
   * <p>The future does not fail on an API or transport error: a request that could not be confirmed
   * as processed is returned in {@link BatchAddResult#getUnprocessedRequests()}. A single request
   * whose JSON exceeds the payload limit is rejected up front with {@link
   * IllegalArgumentException}.
   *
   * <p>Retries match requests by {@link RequestQueueRequest#getUniqueKey()}. Set {@code uniqueKey}
   * explicitly: without it, a request that succeeded on an earlier attempt can still be reported as
   * unprocessed.
   */
  public CompletableFuture<BatchAddResult> batchAddRequests(
      List<RequestQueueRequest> requests, boolean forefront, BatchAddRequestsOptions options) {
    long payloadSizeLimitBytes =
        MAX_PAYLOAD_SIZE_BYTES
            - (long) Math.ceil(MAX_PAYLOAD_SIZE_BYTES * PAYLOAD_SAFETY_BUFFER_PERCENT);

    // Serialize every request exactly once: the byte lengths decide the chunk boundaries and the
    // same bytes are joined into each chunk's request body (and into the body of a retry).
    List<SerializedRequest> entries = new ArrayList<>(requests.size());
    for (RequestQueueRequest request : requests) {
      entries.add(new SerializedRequest(request, Json.toBytes(request)));
    }

    List<List<SerializedRequest>> chunks = new ArrayList<>();
    for (int start = 0; start < entries.size(); ) {
      int end = Math.min(start + MAX_REQUESTS_PER_BATCH, entries.size());
      List<SerializedRequest> countSlice = entries.subList(start, end);
      List<SerializedRequest> chunk = sliceByByteLength(countSlice, payloadSizeLimitBytes, start);
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
    return CancellableFuture.allOf(workers)
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
   * One worker: claims the next unstarted chunk through {@code nextChunkIndex} and processes it,
   * until all chunks are claimed. Running {@code workerCount} workers bounds the chunk requests in
   * flight to {@link BatchAddRequestsOptions#maxParallel}, without a thread pool.
   */
  private CompletableFuture<Void> runBatchAddWorker(
      List<List<SerializedRequest>> chunks,
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
   * forward progress. {@code startIndex} is the position of {@code requests.get(0)} in the caller's
   * full input list, used only to report which request is oversized.
   *
   * @throws IllegalArgumentException if any individual request's own JSON encoding already exceeds
   *     {@code maxByteLength} (no chunk size could ever fit it)
   */
  private static List<SerializedRequest> sliceByByteLength(
      List<SerializedRequest> requests, long maxByteLength, int startIndex) {
    if (jsonArrayByteLength(requests) < maxByteLength) {
      return requests;
    }

    List<SerializedRequest> sliced = new ArrayList<>();
    long byteLength = EMPTY_JSON_ARRAY_BYTES;
    for (int i = 0; i < requests.size(); i++) {
      SerializedRequest request = requests.get(i);
      long itemBytes = request.json().length;
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
      // jsonArrayByteLength's full-array measurement above, rather than relying on being slightly
      // under it.
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

  /** A request together with its JSON encoding, so it is serialized only once. */
  private record SerializedRequest(RequestQueueRequest request, byte[] json) {}

  /** The byte length of the JSON array that joins the given requests' encodings. */
  private static long jsonArrayByteLength(List<SerializedRequest> requests) {
    long length = EMPTY_JSON_ARRAY_BYTES;
    for (SerializedRequest request : requests) {
      length += request.json().length;
    }
    return requests.isEmpty() ? length : length + requests.size() - 1;
  }

  /** The JSON array of the given requests' encodings, as sent in a batch request body. */
  private static byte[] jsonArray(List<SerializedRequest> requests) {
    ByteArrayOutputStream out = new ByteArrayOutputStream((int) jsonArrayByteLength(requests));
    out.write('[');
    for (int i = 0; i < requests.size(); i++) {
      if (i > 0) {
        out.write(',');
      }
      out.writeBytes(requests.get(i).json());
    }
    out.write(']');
    return out.toByteArray();
  }

  /**
   * Adds one chunk (already sized to the API limit), retrying requests the API leaves unprocessed
   * with exponential backoff (see {@link Async}). The returned future never completes
   * exceptionally: after a non-retryable API error or a transport failure, the remaining requests
   * of the chunk are reported as unprocessed.
   */
  private CompletableFuture<BatchAddResult> batchAddChunkWithRetries(
      List<SerializedRequest> chunk, boolean forefront, BatchAddRequestsOptions options) {
    int maxRetries = options.maxUnprocessedRequestsRetriesValue();
    long minDelayMillis = options.minDelayBetweenUnprocessedRequestsRetriesMillisValue();
    return batchAddAttempt(chunk, chunk, List.of(), forefront, 0, maxRetries, minDelayMillis);
  }

  /** One attempt of the retry loop backing {@link #batchAddChunkWithRetries}. */
  private CompletableFuture<BatchAddResult> batchAddAttempt(
      List<SerializedRequest> chunk,
      List<SerializedRequest> toSend,
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
              List<SerializedRequest> stillRemaining = requestsNotYetProcessed(chunk, processed);
              // response == null means the batchAddChunk call failed: stop retrying this chunk and
              // report what is not confirmed as unprocessed.
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
      List<SerializedRequest> chunk, List<RequestQueueOperationInfo> processed) {
    BatchAddResult result = new BatchAddResult();
    result.setProcessedRequests(processed);
    // Unprocessed = everything sent minus everything acknowledged processed. Computing it here
    // (instead of trusting the last response) stays correct even if the API returns fewer entries.
    result.setUnprocessedRequests(
        requestsNotYetProcessed(chunk, processed).stream()
            .map(SerializedRequest::request)
            .toList());
    return result;
  }

  private static List<SerializedRequest> requestsNotYetProcessed(
      List<SerializedRequest> chunk, List<RequestQueueOperationInfo> processed) {
    Set<String> processedKeys = new HashSet<>();
    for (RequestQueueOperationInfo info : processed) {
      if (info.getUniqueKey() != null) {
        processedKeys.add(info.getUniqueKey());
      }
    }
    List<SerializedRequest> remaining = new ArrayList<>();
    for (SerializedRequest entry : chunk) {
      if (!processedKeys.contains(entry.request().getUniqueKey())) {
        remaining.add(entry);
      }
    }
    return remaining;
  }

  /**
   * {@code (1 + random) * 2^attempt * minDelay}: exponential backoff with jitter. The exponent is
   * capped so the delay cannot overflow.
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
      List<SerializedRequest> requests, boolean forefront) {
    QueryParams params = new QueryParams();
    params.addBool("forefront", forefront);
    withClientKey(params);
    return ctx.postWithBody(
        "requests/batch",
        params,
        jsonArray(requests),
        ResourceContext.CONTENT_TYPE_JSON,
        Json.type(BatchAddResult.class),
        Timeout.MEDIUM);
  }

  /**
   * Deletes multiple requests in a single call. Each entry identifies a request (e.g. by id or
   * uniqueKey).
   */
  public CompletableFuture<BatchDeleteResult> batchDeleteRequests(Object requests) {
    QueryParams params = withClientKey(new QueryParams());
    return ctx.deleteWithBody(
        "requests/batch", params, requests, BatchDeleteResult.class, Timeout.SHORT);
  }

  /** Lists the queue's requests with pagination. */
  public CompletableFuture<RequestsList> listRequests(ListRequestsOptions options) {
    options.validate();
    QueryParams params = new QueryParams();
    options.apply(params);
    withClientKey(params);
    return ctx.getResourceRequired("requests", params, RequestsList.class, Timeout.MEDIUM);
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
        RequestLockInfo.class,
        Timeout.MEDIUM);
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
    return http.call("DELETE", url, null, "", Timeout.SHORT)
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
    return ctx.postWithBody(
        "requests/unlock", params, null, "", UnlockRequestsResult.class, Timeout.LONG);
  }

  /**
   * Returns a lazy publisher over all requests in the queue, with pages of up to {@code pageLimit}
   * requests ({@code null} for the server default). Equivalent to {@link #paginateRequests(Long,
   * Long, List) paginateRequests(null, pageLimit, null)}.
   */
  public Flow.Publisher<RequestQueueRequest> paginateRequests(Long pageLimit) {
    return paginateRequests(null, pageLimit, null);
  }

  /**
   * Returns a lazy publisher over the queue's requests, starting from the beginning of the queue.
   *
   * @param totalLimit the maximum number of requests yielded, or {@code null} or non-positive for
   *     all
   * @param chunkSize the page size, or {@code null} for the server default
   * @param filter the request states to include ({@link ListRequestsOptions#FILTER_LOCKED}, {@link
   *     ListRequestsOptions#FILTER_PENDING}), or {@code null} for all
   */
  public Flow.Publisher<RequestQueueRequest> paginateRequests(
      Long totalLimit, Long chunkSize, List<String> filter) {
    return new RequestsPublisher(totalLimit, chunkSize, filter);
  }

  /**
   * Publishes the queue's requests page by page through the cursor-based listing endpoint, as the
   * subscriber signals demand. Like {@link com.apify.client.internal.AsyncPaginatedPublisher}, it
   * supports a single subscriber.
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
      private volatile CompletableFuture<?> inFlight;
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
        CompletableFuture<?> fetch = inFlight;
        if (fetch != null) {
          fetch.cancel(true);
        }
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
        CompletableFuture<RequestsList> fetch =
            ctx.getResourceRequired("requests", params, RequestsList.class, Timeout.MEDIUM);
        inFlight = fetch;
        fetch.whenComplete(
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
        // Defensively trim to the cap in case the server returned more than requested, so a
        // subscriber never sees more than `totalLimit` items even if the server ignores (or
        // overshoots) the requested per-page `limit`.
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
