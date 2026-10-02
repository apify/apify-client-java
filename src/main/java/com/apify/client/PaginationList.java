package com.apify.client;

import java.util.Collections;
import java.util.List;

/**
 * A single page of an offset/limit-paginated list.
 *
 * <p>The pagination metadata ({@link #getTotal() total}, {@link #getOffset() offset}, {@link
 * #getLimit() limit}, {@link #getCount() count}, {@link #isDesc() desc}) accompanies the {@link
 * #getItems() items}. Note: {@code total} reflects the API's reported total, which can briefly lag
 * immediately after a write (e.g. right after pushing items) because the count is computed
 * asynchronously — re-read after a short delay if you need an exact post-write total.
 *
 * @param <T> the item type
 */
public final class PaginationList<T> extends ApifyResource {

  private long total;
  private long offset;
  private long limit;
  private long count;
  private boolean desc;
  private List<T> items = List.of();
  private Long scannedCount;

  /** Total number of items available across all pages. */
  public long getTotal() {
    return total;
  }

  /** Number of items skipped at the start. */
  public long getOffset() {
    return offset;
  }

  /** Maximum number of items the API would return for this request. */
  public long getLimit() {
    return limit;
  }

  /** Number of items actually returned in this page. */
  public long getCount() {
    return count;
  }

  /** Whether the items are in descending order. */
  public boolean isDesc() {
    return desc;
  }

  /**
   * The number of rows the API scanned to produce this page, before any item-level filtering (e.g.
   * dataset items' {@code clean}/{@code skipEmpty}/{@code skipHidden}), as reported by the {@code
   * X-Apify-Pagination-Count} response header. {@code null} when the endpoint does not send that
   * header, in which case {@link #getCount()} (the number of items actually returned) is the right
   * value to advance pagination by - the two always agree outside the dataset items endpoint.
   *
   * <p>A value smaller than {@link #getCount()} cannot be a genuine "rows scanned" answer (scanning
   * can never produce more items than it scanned), so it is treated as the header not actually
   * being populated for that request rather than as real data - callers reading this field directly
   * should apply the same {@code scannedCount != null && scannedCount >= getCount() ? scannedCount
   * : getCount()} fallback that {@link com.apify.client.dataset.DatasetClient#iterateItems} uses.
   *
   * <p>Internal pagination-engine detail, exposed here (rather than hidden) only because {@link
   * #getItems()} and this metadata necessarily travel together on the same page object; most
   * callers never need it directly - {@code iterateItems} already accounts for it.
   */
  public Long getScannedCount() {
    return scannedCount;
  }

  /** The items of this page (never {@code null}; unmodifiable). */
  public List<T> getItems() {
    // Null-coalesce: Jackson binds directly to the (private) `items` field for deserialization
    // (see Json's FIELD/ANY visibility config), which bypasses setItems()'s List.copyOf() default
    // whenever the API response contains an explicit `"items": null`. Falling back to List.of()
    // here keeps the "never null" contract regardless of how the field was populated.
    return items == null ? List.of() : Collections.unmodifiableList(items);
  }

  // Public (not package-private) because DatasetClient, in a different package, builds a page's
  // metadata from response headers one field at a time and Java has no cross-package visibility
  // between "public" and "package-private".
  public void setTotal(long total) {
    this.total = total;
  }

  public void setOffset(long offset) {
    this.offset = offset;
  }

  public void setLimit(long limit) {
    this.limit = limit;
  }

  public void setCount(long count) {
    this.count = count;
  }

  public void setDesc(boolean desc) {
    this.desc = desc;
  }

  public void setScannedCount(Long scannedCount) {
    this.scannedCount = scannedCount;
  }

  public void setItems(List<T> items) {
    // Copy defensively: items is set once by the client from a freshly-parsed/collected list, but
    // taking an immutable copy means a caller can never mutate this page's items afterwards by
    // mutating the list it passed in, and getItems() needs no wrapping to stay unmodifiable.
    this.items = List.copyOf(items);
  }
}
