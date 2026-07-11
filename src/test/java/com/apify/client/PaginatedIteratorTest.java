package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/**
 * Hermetic (token-free) tests for the offset/limit iteration engine. They drive {@link
 * PaginatedIterator} with a stub page fetcher over a synthetic collection, pinning the total-cap /
 * chunk-size arithmetic, server-side page-size clamping, and termination — without any network or
 * {@code APIFY_TOKEN}.
 */
class PaginatedIteratorTest {

  /**
   * A fake paginated endpoint over integers {@code [0, available)}. Like the real API, it clamps a
   * requested page size (or an unset one) to {@code maxPageSize} ({@code 0} = no clamp).
   */
  private static final class StubFetcher implements PaginatedIterator.PageFetcher<Integer> {
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
    public PaginationList<Integer> fetch(long offset, Long limit) {
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
      return page;
    }
  }

  private static List<Integer> drain(java.util.Iterator<Integer> it) {
    List<Integer> out = new ArrayList<>();
    while (it.hasNext()) {
      out.add(it.next());
    }
    return out;
  }

  @Test
  void totalCapTrimsLastPage() {
    StubFetcher f = new StubFetcher(10);
    List<Integer> got = drain(new PaginatedIterator<>(3L, 2L, null, f));
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
    List<Integer> got = drain(new PaginatedIterator<>(null, 2L, null, f));
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
    List<Integer> got = drain(new PaginatedIterator<>(null, 100L, null, f));
    assertEquals(List.of(0, 1, 2, 3, 4), got, "clamped short pages must keep paging to the end");
  }

  @Test
  void capIsHonoredEvenWhenServerClampsPages() {
    StubFetcher f = new StubFetcher(100, 2);
    List<Integer> got = drain(new PaginatedIterator<>(5L, 100L, null, f));
    assertEquals(List.of(0, 1, 2, 3, 4), got, "the total cap holds despite server page clamping");
  }

  @Test
  void noChunkUsesServerDefaultAndPagesToEmpty() {
    StubFetcher f = new StubFetcher(4);
    List<Integer> got = drain(new PaginatedIterator<>(null, null, null, f));
    assertEquals(List.of(0, 1, 2, 3), got);
    assertEquals(-1L, f.requests.get(0)[1], "no cap and no chunk => null limit (server default)");
  }

  @Test
  void startOffsetIsHonored() {
    StubFetcher f = new StubFetcher(10);
    List<Integer> got = drain(new PaginatedIterator<>(null, 5L, 7L, f));
    assertEquals(List.of(7, 8, 9), got, "iteration starts at the requested offset");
    assertEquals(7L, f.requests.get(0)[0]);
  }

  @Test
  void trimsOverReturnedPageToCap() {
    // A misbehaving server that returns more items than requested must not make the iterator
    // overshoot the caller's total cap.
    PaginatedIterator.PageFetcher<Integer> overReturning =
        (offset, limit) -> {
          PaginationList<Integer> page = new PaginationList<>();
          page.setItems(List.of(1, 2, 3, 4, 5)); // ignores the requested limit
          page.setTotal(5);
          page.setCount(5);
          return page;
        };
    List<Integer> got = drain(new PaginatedIterator<>(3L, 2L, null, overReturning));
    assertEquals(List.of(1, 2, 3), got, "the last page is trimmed to the cap");
  }

  @Test
  void emptyCollectionYieldsNothing() {
    StubFetcher f = new StubFetcher(0);
    java.util.Iterator<Integer> it = new PaginatedIterator<>(null, 5L, null, f);
    assertFalse(it.hasNext());
    assertThrows(NoSuchElementException.class, it::next);
  }
}
