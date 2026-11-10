package com.apify.client.internal;

import com.apify.client.PaginationList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A lazy, backpressure-aware {@link Flow.Publisher} over an offset/limit-paginated list endpoint.
 * It fetches one page at a time, as the subscriber signals demand, and supports a single
 * subscriber.
 *
 * <p>{@code totalLimit} caps the number of rows read across all pages ({@code null} reads
 * everything). {@code chunkSize} is the page size ({@code null} lets the API choose); each page
 * requests at most the rows left under the cap. A row is an item unless a page's {@link
 * PaginationList#getCount() count} differs from the items it returns; all returned items are
 * yielded.
 *
 * <p>Each page advances the offset by the rows it covered, capped at the requested page limit.
 * Iteration stops when the cap is reached or a page covers no rows. It does not stop on a short
 * page or on {@code total}: the API clamps large page sizes, and {@code total} can be {@code 0} or
 * lag right after a write. The cost is one extra request at the end.
 */
public final class AsyncPaginatedPublisher<T> implements Flow.Publisher<T> {

  /** Fetches a single page starting at {@code offset}, requesting at most {@code limit} items. */
  @FunctionalInterface
  public interface PageFetcher<T> {
    CompletableFuture<PaginationList<T>> fetch(long offset, Long limit);
  }

  private final PageFetcher<T> fetcher;
  private final Long totalLimit;
  private final Long chunkSize;
  private final Long startOffset;
  private final AtomicBoolean subscribed = new AtomicBoolean();

  public AsyncPaginatedPublisher(
      Long totalLimit, Long chunkSize, Long startOffset, PageFetcher<T> fetcher) {
    this.totalLimit = totalLimit != null && totalLimit > 0 ? totalLimit : null;
    this.chunkSize = chunkSize != null && chunkSize > 0 ? chunkSize : null;
    this.startOffset = startOffset != null && startOffset > 0 ? startOffset : 0;
    this.fetcher = fetcher;
  }

  @Override
  public void subscribe(Flow.Subscriber<? super T> subscriber) {
    if (!subscribed.compareAndSet(false, true)) {
      subscriber.onSubscribe(NoopSubscription.INSTANCE);
      subscriber.onError(
          new IllegalStateException(
              "This publisher supports only a single subscriber (already subscribed)"));
      return;
    }
    Session session = new Session(subscriber);
    subscriber.onSubscribe(session);
  }

  /**
   * A subscription with no demand, used to reject a second subscriber per Reactive Streams §1.9.
   */
  private enum NoopSubscription implements Flow.Subscription {
    INSTANCE;

    @Override
    public void request(long n) {}

    @Override
    public void cancel() {}
  }

  /**
   * Per-subscriber paging state and the {@link Flow.Subscription} driving it. Not shared across
   * subscribers - each {@link #subscribe} call gets its own instance.
   */
  private final class Session implements Flow.Subscription {
    private final Flow.Subscriber<? super T> subscriber;
    private final AtomicLong requested = new AtomicLong();
    private final AtomicBoolean draining = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean terminated = new AtomicBoolean();

    // Only touched inside the `draining`-guarded section, which admits one drain loop at a time,
    // so these fields need no further synchronization.
    private List<T> buffer = List.of();
    private int pos;
    private long offset;
    private long rowsRead;
    private boolean exhausted;
    private boolean fetchInFlight;
    private Long requestedLimit;
    private volatile CompletableFuture<?> inFlight;

    Session(Flow.Subscriber<? super T> subscriber) {
      this.subscriber = subscriber;
      this.offset = startOffset;
    }

    @Override
    public void request(long n) {
      if (n <= 0) {
        // Not terminate(): request() does not hold the `draining` guard, and releasing it here
        // could start a second drain loop that races on the paging fields.
        if (terminated.compareAndSet(false, true)) {
          subscriber.onError(
              new IllegalArgumentException(
                  "Reactive Streams violation: requested amount must be positive, was " + n));
        }
        return;
      }
      requested.updateAndGet(current -> addSaturating(current, n));
      drain();
    }

    @Override
    public void cancel() {
      cancelled.set(true);
      // Abort the page being fetched, with its request and retries.
      CompletableFuture<?> fetch = inFlight;
      if (fetch != null) {
        fetch.cancel(true);
      }
    }

    /** Enters the drain loop unless another drain (of this same session) is already running. */
    private void drain() {
      if (draining.compareAndSet(false, true)) {
        drainLoop();
      }
    }

    /**
     * Emits buffered items while there is demand, then fetches the next page or completes. The
     * {@code draining} flag is released once per entry, directly or from the page fetch's callback.
     */
    private void drainLoop() {
      while (!cancelled.get() && !terminated.get() && requested.get() > 0 && pos < buffer.size()) {
        T item = buffer.get(pos++);
        requested.decrementAndGet();
        subscriber.onNext(item);
      }
      if (cancelled.get() || terminated.get()) {
        // `terminated` means a reentrant request(n<=0) delivered onError from onNext above.
        draining.set(false);
        return;
      }
      if (pos < buffer.size()) {
        // Out of demand with items buffered.
        draining.set(false);
        // Re-check: a request() may have arrived between the loop's last check and the release.
        if (requested.get() > 0 && pos < buffer.size()) {
          drain();
        }
        return;
      }
      if (exhausted) {
        complete();
        return;
      }
      if (fetchInFlight) {
        // A fetch is still outstanding; its completion resumes this loop.
        draining.set(false);
        return;
      }
      fetchNextPage();
    }

    private void fetchNextPage() {
      fetchInFlight = true;
      Long capRemaining = totalLimit != null ? totalLimit - rowsRead : null;
      Long pageLimit = minForLimit(capRemaining, chunkSize);
      requestedLimit = pageLimit;
      CompletableFuture<PaginationList<T>> fetch = fetcher.fetch(offset, pageLimit);
      inFlight = fetch;
      fetch.whenComplete(
          (page, error) -> {
            fetchInFlight = false;
            if (cancelled.get()) {
              draining.set(false);
              return;
            }
            if (error != null) {
              terminate(() -> subscriber.onError(HttpClientCore.unwrapCompletion(error)));
              return;
            }
            applyPage(page);
            drainLoop();
          });
    }

    private void applyPage(PaginationList<T> page) {
      buffer = page.getItems();
      pos = 0;
      int count = buffer.size();
      // The rows the page covered (see PaginationList#getCount), capped at the rows requested.
      long scanned = Math.max(page.getCount(), count);
      if (requestedLimit != null) {
        scanned = Math.min(scanned, requestedLimit);
      }
      offset += scanned;
      rowsRead += scanned;
      // Stop on the cap or an empty page, never on a short page or the reported total.
      if (scanned == 0 || (totalLimit != null && rowsRead >= totalLimit)) {
        exhausted = true;
      }
    }

    private void complete() {
      terminate(subscriber::onComplete);
    }

    /** Runs a terminal subscriber callback at most once, then releases the drain guard. */
    private void terminate(Runnable callback) {
      if (terminated.compareAndSet(false, true)) {
        callback.run();
      }
      draining.set(false);
    }
  }

  /** Returns the smaller of two positive-or-{@code null} bounds, or {@code null} if both are. */
  private static Long minForLimit(Long a, Long b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return Math.min(a, b);
  }

  /** Adds {@code b} to {@code a}, saturating at {@link Long#MAX_VALUE} instead of overflowing. */
  private static long addSaturating(long a, long b) {
    long sum = a + b;
    return sum < 0 ? Long.MAX_VALUE : sum;
  }
}
