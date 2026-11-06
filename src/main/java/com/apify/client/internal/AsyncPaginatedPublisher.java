package com.apify.client.internal;

import com.apify.client.PaginationList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A lazy, backpressure-aware {@link Flow.Publisher} over an offset/limit-paginated list endpoint,
 * fetching one page at a time as demand allows. Internal reusable engine shared by every
 * offset/limit-paginated collection's {@code iterate} method, so the paging arithmetic lives in one
 * place (DRY). (The cursor-based {@code iterateKeys} and the single-fetch {@code versions}/{@code
 * env-vars} iterators do not use it.)
 *
 * <p>This is the client's "reactive" iteration primitive: a {@link java.util.concurrent.Flow}
 * ({@code java.util.concurrent.Flow}, the JDK's built-in Reactive Streams interfaces) publisher,
 * chosen over a blocking {@link java.util.Iterator} so that following a large paginated collection
 * never blocks a thread waiting on the next page's network round-trip. Pages are fetched only as
 * the subscriber signals demand via {@link Flow.Subscription#request(long)}.
 *
 * <p>Behaviour, mirroring the end-user contract of the reference JS client's iterable {@code
 * list()}:
 *
 * <ul>
 *   <li>{@code totalLimit} caps the <em>total</em> number of rows read across all pages ({@code
 *       null} = read everything). For every list but dataset items a row is an item; a page of
 *       dataset items can return fewer items than the rows it covers (filters) or more ({@code
 *       unwind}), and all of them are yielded.
 *   <li>{@code chunkSize} is the per-request page size ({@code null} = let the API choose). Each
 *       page requests {@code min(remaining-under-cap, chunkSize)} rows so the cap is never
 *       overshot.
 * </ul>
 *
 * <p>Each page advances the offset by the rows it covered, as {@link PaginationList#getCount()}
 * defines them, capped at the page limit that was requested. Iteration stops only when the cap is
 * reached or a page covers no rows. It deliberately does <em>not</em> stop on a short page or a
 * reported {@code total}: the API clamps an over-large requested page size to its own maximum (so a
 * "short" page is common mid-collection), and some endpoints report a {@code total} of {@code 0} or
 * a value that lags right after a write (e.g. dataset items) — either signal would truncate
 * iteration. Terminating on an empty page costs one extra request at the end but yields the
 * complete result in every case.
 *
 * <p>Supports a single subscriber (as permitted by the Reactive Streams specification); subscribing
 * twice completes the second subscriber's subscription with an error instead of sharing state
 * between them.
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

    // Only ever touched from within a `draining`-guarded section, which - by construction below -
    // never runs concurrently with itself (the flag admits only one drain loop at a time, and every
    // continuation after an async page fetch re-enters through the same guarded path), so no
    // separate synchronization on these fields is needed.
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
        // Deliberately does not go through terminate(): request() is called from outside the
        // drain loop (it does not hold the `draining` guard), so - unlike the drain loop's own
        // terminal paths - it must not release a guard it may not own. Releasing it here would
        // let a concurrent request() elsewhere win the drain() CAS and start a second drain loop
        // in parallel with a possibly still-running one, racing on the unsynchronized paging
        // fields (mirrors ListPublisher.Session's identical n<=0 handling).
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
     * Emits buffered items while there is demand, fetches the next page when the buffer runs dry
     * (or completes the subscriber when the collection is exhausted), and always releases the
     * {@code draining} flag exactly once per entry - either directly, or (when a page fetch is
     * started) from that fetch's completion callback, which re-enters this same method.
     */
    private void drainLoop() {
      while (!cancelled.get() && !terminated.get() && requested.get() > 0 && pos < buffer.size()) {
        T item = buffer.get(pos++);
        requested.decrementAndGet();
        subscriber.onNext(item);
      }
      if (cancelled.get() || terminated.get()) {
        // `terminated` here means a reentrant request(n<=0) fired onError from within the onNext
        // call just above (a subscriber violating Reactive Streams §3.9); stop emitting immediately
        // instead of continuing to drain the rest of the buffered demand into a subscriber that has
        // already received a terminal signal (RS §1.7).
        draining.set(false);
        return;
      }
      if (pos < buffer.size()) {
        // Demand exhausted (requested == 0) with items still buffered: stop until more is
        // requested.
        draining.set(false);
        // Re-check demand: a request() on another thread may have arrived between the loop's last
        // check and this flag release, which would otherwise be a missed wakeup.
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
        // A fetch from an earlier drain() is still outstanding; its completion will resume this
        // loop. With a well-behaved subscriber this is unreachable (the flag is only set/cleared
        // within this draining-guarded section, so no concurrent drain can normally observe it
        // true here) - but it is a real safety net, not dead code: request(n<=0) no longer
        // releases `draining` on its own (see the comment there), yet a subscriber that itself
        // races two concurrent request() calls could still reach this branch, and it correctly
        // defers to the in-flight fetch's own completion instead of starting a second one.
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
      // The rows the page covered, which are not the items it returned: see
      // PaginationList#getCount.
      // The count of a dataset page can lag a fresh push, so it is raised to the items returned,
      // and capped at the rows requested so that an unwound page does not advance past rows the
      // next request would never read.
      long scanned = Math.max(page.getCount(), count);
      if (requestedLimit != null) {
        scanned = Math.min(scanned, requestedLimit);
      }
      offset += scanned;
      rowsRead += scanned;
      // Stop on the cap or a page that covered nothing (the source is exhausted); never on a short
      // *returned* page (the API clamps large page sizes, and filtering can legitimately return
      // fewer items than the rows covered) or the reported total (unreliable on some endpoints).
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

  /**
   * Returns the smaller of the two per-page bounds, or {@code null} (server default) when neither
   * is set. Both inputs are already positive-or-{@code null}: {@code chunkSize} is normalized in
   * the constructor and {@code capRemaining} is only ever {@code > 0} here (the {@code exhausted}
   * guard blocks fetching once the cap is reached).
   */
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
