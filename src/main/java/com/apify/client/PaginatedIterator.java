package com.apify.client;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A lazy {@link Iterator} over an offset/limit-paginated list endpoint, fetching one page at a
 * time. Internal reusable engine shared by every collection client's {@code iterate} method, so the
 * paging arithmetic lives in one place (DRY).
 *
 * <p>Behaviour, mirroring the end-user contract of the reference JS client's iterable {@code
 * list()}:
 *
 * <ul>
 *   <li>{@code totalLimit} caps the <em>total</em> number of items yielded across all pages ({@code
 *       null} = yield everything).
 *   <li>{@code chunkSize} is the per-request page size ({@code null} = let the API choose). Each
 *       page requests {@code min(remaining-under-cap, chunkSize)} items so the cap is never
 *       overshot.
 * </ul>
 *
 * <p>Each page advances the offset by the number of items actually returned, and iteration stops
 * only when the cap is reached or the API returns an empty page. It deliberately does <em>not</em>
 * stop on a short page or a reported {@code total}: the API clamps an over-large requested page
 * size to its own maximum (so a "short" page is common mid-collection), and some endpoints report a
 * {@code total} of {@code 0} or a value that lags right after a write (e.g. dataset items) — either
 * signal would truncate iteration. Terminating on an empty page costs one extra request at the end
 * but yields the complete result in every case.
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
    this.chunkSize = chunkSize != null && chunkSize > 0 ? chunkSize : null;
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
    // Defensively trim the last page to the cap in case the server returned more than requested, so
    // the caller never sees more than `totalLimit` items.
    if (totalLimit != null && yielded > totalLimit) {
      buffer = buffer.subList(0, count - (int) (yielded - totalLimit));
      yielded = totalLimit;
    }
    // Stop on the caller's cap or an empty page; never on a short page (the API clamps large page
    // sizes) or the reported total (unreliable on some endpoints — see class doc).
    if (count == 0 || (totalLimit != null && yielded >= totalLimit)) {
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
