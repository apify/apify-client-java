package com.apify.client;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A lazy {@link Iterator} over an offset/limit-paginated list endpoint, fetching one page at a
 * time. Internal reusable engine shared by every collection client's {@code iterate} method, so the
 * paging arithmetic lives in one place (DRY).
 *
 * <p>The end-user behaviour matches the reference JS client's {@code _listPaginatedFromCallback}:
 *
 * <ul>
 *   <li>{@code totalLimit} caps the <em>total</em> number of items yielded across all pages ({@code
 *       null} = yield everything).
 *   <li>{@code chunkSize} is the per-request page size ({@code null} = let the API choose). Each
 *       page requests {@code min(remaining-under-cap, chunkSize)} items so the cap is never
 *       overshot.
 * </ul>
 *
 * <p>Iteration stops when the cap is reached, the API returns an empty page, or a page comes back
 * shorter than requested (the last page). The short-page check — rather than relying solely on the
 * reported {@code total} — makes iteration robust to a momentarily under-reported {@code total}
 * (the count can lag right after a write), which would otherwise truncate a full page. The reported
 * total is only consulted to terminate the "unbounded, server-chosen page size" case, where no page
 * size is known to compare against.
 */
final class PaginatedIterator<T> implements Iterator<T> {

  /** Fetches a single page starting at {@code offset}, requesting at most {@code limit} items. */
  @FunctionalInterface
  interface PageFetcher<T> {
    PaginationList<T> fetch(long offset, Long limit);
  }

  private final PageFetcher<T> fetcher;
  private final Long totalLimit;
  private final Long chunkSize;

  private List<T> buffer = List.of();
  private int pos;
  private long offset;
  private long yielded;
  private boolean exhausted;

  PaginatedIterator(Long totalLimit, Long chunkSize, Long startOffset, PageFetcher<T> fetcher) {
    this.totalLimit = totalLimit != null && totalLimit > 0 ? totalLimit : null;
    this.chunkSize = chunkSize;
    this.offset = startOffset != null && startOffset > 0 ? startOffset : 0;
    this.fetcher = fetcher;
  }

  @Override
  public boolean hasNext() {
    while (pos >= buffer.size()) {
      if (exhausted) {
        return false;
      }
      fetchNextPage();
    }
    return true;
  }

  @Override
  public T next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    return buffer.get(pos++);
  }

  private void fetchNextPage() {
    Long capRemaining = totalLimit != null ? totalLimit - yielded : null;
    Long pageLimit = minForLimit(capRemaining, chunkSize);
    PaginationList<T> page = fetcher.fetch(offset, pageLimit);
    buffer = page.getItems();
    pos = 0;
    int count = buffer.size();
    offset += count;
    yielded += count;

    boolean capReached = totalLimit != null && yielded >= totalLimit;
    boolean shortPage = pageLimit != null && count < pageLimit;
    // With no explicit page size the API returns its default page, so there is nothing to compare a
    // short page against — fall back to the reported total to detect the end of the collection.
    boolean totalReached = pageLimit == null && offset >= page.getTotal();
    if (count == 0 || capReached || shortPage || totalReached) {
      exhausted = true;
    }
  }

  /**
   * The API treats {@code 0} as "unset" for a limit, so this returns the smaller of the two
   * positive bounds, or {@code null} (server default) when neither is a positive value.
   */
  private static Long minForLimit(Long a, Long b) {
    Long x = a != null && a > 0 ? a : null;
    Long y = b != null && b > 0 ? b : null;
    if (x == null) {
      return y;
    }
    if (y == null) {
      return x;
    }
    return Math.min(x, y);
  }
}
