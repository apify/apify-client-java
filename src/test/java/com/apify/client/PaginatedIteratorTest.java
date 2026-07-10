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
 * chunk-size arithmetic and termination that mirror the reference JS {@code
 * _listPaginatedFromCallback} — without any network or {@code APIFY_TOKEN}.
 */
class PaginatedIteratorTest {

  /** A fake paginated endpoint over integers {@code [0, available)} reporting {@code total}. */
  private static final class StubFetcher implements PaginatedIterator.PageFetcher<Integer> {
    final long total;
    final long available;
    final List<long[]> requests = new ArrayList<>();

    StubFetcher(long total) {
      this(total, total);
    }

    StubFetcher(long total, long available) {
      this.total = total;
      this.available = available;
    }

    @Override
    public PaginationList<Integer> fetch(long offset, Long limit) {
      requests.add(new long[] {offset, limit == null ? -1 : limit});
      long take = limit == null ? Long.MAX_VALUE : limit;
      List<Integer> items = new ArrayList<>();
      for (long i = offset; i < available && items.size() < take; i++) {
        items.add((int) i);
      }
      PaginationList<Integer> page = new PaginationList<>();
      page.setItems(items);
      page.setTotal(total);
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
    // First page requests min(cap,chunk)=2; second page requests min(remaining=1,chunk=2)=1.
    assertEquals(2, f.requests.size());
    assertEquals(0L, f.requests.get(0)[0]);
    assertEquals(2L, f.requests.get(0)[1]);
    assertEquals(2L, f.requests.get(1)[0]);
    assertEquals(1L, f.requests.get(1)[1]);
  }

  @Test
  void chunkSizePagesAll() {
    StubFetcher f = new StubFetcher(5);
    List<Integer> got = drain(new PaginatedIterator<>(null, 2L, null, f));
    assertEquals(List.of(0, 1, 2, 3, 4), got);
    assertEquals(3, f.requests.size(), "5 items / page size 2 => 3 pages");
  }

  @Test
  void noCapNoChunkUsesServerDefault() {
    StubFetcher f = new StubFetcher(4);
    List<Integer> got = drain(new PaginatedIterator<>(null, null, null, f));
    assertEquals(List.of(0, 1, 2, 3), got);
    assertEquals(1, f.requests.size());
    assertEquals(-1L, f.requests.get(0)[1], "no cap and no chunk => null limit (server default)");
  }

  @Test
  void capLargerThanTotalYieldsAll() {
    StubFetcher f = new StubFetcher(4);
    assertEquals(List.of(0, 1, 2, 3), drain(new PaginatedIterator<>(100L, null, null, f)));
  }

  @Test
  void startOffsetIsHonored() {
    StubFetcher f = new StubFetcher(10);
    List<Integer> got = drain(new PaginatedIterator<>(null, null, 7L, f));
    assertEquals(List.of(7, 8, 9), got, "iteration starts at the requested offset");
    assertEquals(7L, f.requests.get(0)[0]);
  }

  @Test
  void emptyCollectionYieldsNothing() {
    StubFetcher f = new StubFetcher(0);
    java.util.Iterator<Integer> it = new PaginatedIterator<>(null, 5L, null, f);
    assertFalse(it.hasNext());
    assertThrows(NoSuchElementException.class, it::next);
  }
}
