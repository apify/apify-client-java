package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.internal.AsyncPaginatedPublisher;
import com.apify.client.internal.ListPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Hermetic (token-free) tests for the offset/limit async iteration engine. They drive {@link
 * AsyncPaginatedPublisher} with a stub page fetcher over a synthetic collection, pinning the
 * total-cap / chunk-size arithmetic, server-side page-size clamping, and termination — without any
 * network or {@code APIFY_TOKEN}.
 */
class AsyncPaginatedPublisherTest {

  /**
   * A fake paginated endpoint over integers {@code [0, available)}. Like the real API, it clamps a
   * requested page size (or an unset one) to {@code maxPageSize} ({@code 0} = no clamp).
   */
  private static final class StubFetcher implements AsyncPaginatedPublisher.PageFetcher<Integer> {
    final long available;
    final long maxPageSize;
    final List<long[]> requests = new ArrayList<>();

    StubFetcher(long available) {
      this(available, 0);
    }

    StubFetcher(long available, long maxPageSize) {
      this.available = available;
      this.maxPageSize = maxPageSize;
    }

    @Override
    public synchronized CompletableFuture<PaginationList<Integer>> fetch(long offset, Long limit) {
      requests.add(new long[] {offset, limit == null ? -1 : limit});
      long requested = limit == null ? Long.MAX_VALUE : limit;
      long take = maxPageSize > 0 ? Math.min(requested, maxPageSize) : requested;
      List<Integer> items = new ArrayList<>();
      for (long i = offset; i < available && items.size() < take; i++) {
        items.add((int) i);
      }
      PaginationList<Integer> page = new PaginationList<>();
      page.setItems(items);
      // Some endpoints (e.g. dataset items) report a total of 0 or a lagging value; the engine must
      // not depend on it, so the stub deliberately reports an unhelpful total.
      page.setTotal(0);
      page.setOffset(offset);
      page.setCount(items.size());
      return CompletableFuture.completedFuture(page);
    }
  }

  private static List<Integer> drain(Flow.Publisher<Integer> publisher) {
    return Publishers.collect(publisher).join();
  }

  @Test
  void totalCapTrimsLastPage() {
    StubFetcher f = new StubFetcher(10);
    List<Integer> got = drain(new AsyncPaginatedPublisher<>(3L, 2L, null, f));
    assertEquals(List.of(0, 1, 2), got, "limit=3 caps the total yielded across pages");
    // First page requests min(cap,chunk)=2; second requests min(remaining=1,chunk=2)=1; the cap is
    // reached exactly, so no trailing empty request is made.
    assertEquals(2, f.requests.size());
    assertEquals(0L, f.requests.get(0)[0]);
    assertEquals(2L, f.requests.get(0)[1]);
    assertEquals(2L, f.requests.get(1)[0]);
    assertEquals(1L, f.requests.get(1)[1]);
  }

  @Test
  void chunkSizePagesAllThenStopsOnEmptyPage() {
    StubFetcher f = new StubFetcher(5);
    List<Integer> got = drain(new AsyncPaginatedPublisher<>(null, 2L, null, f));
    assertEquals(List.of(0, 1, 2, 3, 4), got);
    // 5 items / page size 2 => pages of 2,2,1 then a trailing empty page confirms the end.
    assertEquals(4, f.requests.size());
    assertEquals(5L, f.requests.get(3)[0], "trailing request pages past the last item");
    assertEquals(2L, f.requests.get(3)[1], "each page still requests the chunk size");
  }

  @Test
  void serverClampsLargePageSizeSoShortPageIsNotTheEnd() {
    // Server caps every page at 2 items; the caller asks for far more. A page shorter than the
    // request must NOT be treated as end-of-collection.
    StubFetcher f = new StubFetcher(5, 2);
    List<Integer> got = drain(new AsyncPaginatedPublisher<>(null, 100L, null, f));
    assertEquals(List.of(0, 1, 2, 3, 4), got, "clamped short pages must keep paging to the end");
  }

  @Test
  void capIsHonoredEvenWhenServerClampsPages() {
    StubFetcher f = new StubFetcher(100, 2);
    List<Integer> got = drain(new AsyncPaginatedPublisher<>(5L, 100L, null, f));
    assertEquals(List.of(0, 1, 2, 3, 4), got, "the total cap holds despite server page clamping");
  }

  @Test
  void noChunkUsesServerDefaultAndPagesToEmpty() {
    StubFetcher f = new StubFetcher(4);
    List<Integer> got = drain(new AsyncPaginatedPublisher<>(null, null, null, f));
    assertEquals(List.of(0, 1, 2, 3), got);
    assertEquals(-1L, f.requests.get(0)[1], "no cap and no chunk => null limit (server default)");
  }

  @Test
  void startOffsetIsHonored() {
    StubFetcher f = new StubFetcher(10);
    List<Integer> got = drain(new AsyncPaginatedPublisher<>(null, 5L, 7L, f));
    assertEquals(List.of(7, 8, 9), got, "iteration starts at the requested offset");
    assertEquals(7L, f.requests.get(0)[0]);
  }

  @Test
  void trimsOverReturnedPageToCap() {
    // A misbehaving server that returns more items than requested must not make the publisher
    // overshoot the caller's total cap.
    AsyncPaginatedPublisher.PageFetcher<Integer> overReturning =
        (offset, limit) -> {
          PaginationList<Integer> page = new PaginationList<>();
          page.setItems(List.of(1, 2, 3, 4, 5)); // ignores the requested limit
          page.setTotal(5);
          page.setCount(5);
          return CompletableFuture.completedFuture(page);
        };
    List<Integer> got = drain(new AsyncPaginatedPublisher<>(3L, 2L, null, overReturning));
    assertEquals(List.of(1, 2, 3), got, "the last page is trimmed to the cap");
  }

  @Test
  void emptyCollectionYieldsNothing() {
    StubFetcher f = new StubFetcher(0);
    List<Integer> got = drain(new AsyncPaginatedPublisher<>(null, 5L, null, f));
    assertTrue(got.isEmpty());
  }

  /**
   * Records onNext/onError deliveries for the reentrant-invalid-request regression tests below. A
   * subscriber violating Reactive Streams §3.9 by issuing {@code request(n<=0)} from within {@code
   * onNext} must see exactly the one item already being emitted when the violation is reported,
   * then a single {@code onError} - never further {@code onNext} calls for the rest of an
   * already-fetched/already-fully-in-hand page (RS §1.7: no signals after a terminal one).
   */
  private static final class RecordingSubscriber implements Flow.Subscriber<Integer> {
    final List<Integer> items = new ArrayList<>();
    final List<Throwable> errors = new ArrayList<>();
    final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

    @Override
    public void onSubscribe(Flow.Subscription s) {
      subscription.set(s);
      s.request(5);
    }

    @Override
    public void onNext(Integer item) {
      items.add(item);
      subscription.get().request(-1); // reentrant RS §3.9 violation
    }

    @Override
    public void onError(Throwable t) {
      errors.add(t);
    }

    @Override
    public void onComplete() {}
  }

  @Test
  void reentrantInvalidRequestStopsEmissionImmediately() {
    // Regression test: the drain loop used to check only `cancelled`/demand/buffer-position, never
    // `terminated`, so a reentrant request(n<=0) mid-emission still let the loop emit the rest of
    // an
    // already-fetched page (and even fetch and emit a further page) after onError had already
    // fired.
    StubFetcher f = new StubFetcher(5);
    RecordingSubscriber subscriber = new RecordingSubscriber();
    new AsyncPaginatedPublisher<>(null, null, null, f).subscribe(subscriber);
    assertEquals(
        List.of(0),
        subscriber.items,
        "only the item already being emitted when the invalid request() fired is delivered");
    assertEquals(1, subscriber.errors.size());
    assertTrue(subscriber.errors.get(0) instanceof IllegalArgumentException);
  }

  @Test
  void listPublisherReentrantInvalidRequestStopsEmissionImmediately() {
    // Same regression, for ListPublisher's independent (but structurally identical) drain loop.
    CompletableFuture<List<Integer>> source =
        CompletableFuture.completedFuture(List.of(0, 1, 2, 3, 4));
    RecordingSubscriber subscriber = new RecordingSubscriber();
    new ListPublisher<>(source).subscribe(subscriber);
    assertEquals(
        List.of(0),
        subscriber.items,
        "only the item already being emitted when the invalid request() fired is delivered");
    assertEquals(1, subscriber.errors.size());
    assertTrue(subscriber.errors.get(0) instanceof IllegalArgumentException);
  }
}
