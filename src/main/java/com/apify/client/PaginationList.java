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

  /**
   * Number of entries on this page. On a page from {@link
   * com.apify.client.dataset.DatasetClient#listItems}, the number of dataset rows the API scanned
   * for it, or the number of items returned when that is larger. Filters ({@code clean}, {@code
   * skipEmpty}) can leave it above the number of items returned. The next page starts at {@code
   * offset + Math.min(count, limit)}; the cap matters only with {@code unwind}, which can return
   * more items than the rows scanned. On any other list it is usually the number of items returned;
   * when the API drops entries after counting them, as the Store list can, it is the number
   * counted.
   */
  public long getCount() {
    return count;
  }

  /** Whether the items are in descending order. */
  public boolean isDesc() {
    return desc;
  }

  /**
   * The raw value of the {@code X-Apify-Pagination-Count} response header of a dataset items page:
   * the number of rows the API scanned to produce it. {@code null} when the header is absent, which
   * is always the case outside the dataset items endpoint.
   *
   * @deprecated {@link #getCount()} reports the scanned rows, or the items returned when that is
   *     larger, which is what pagination has to advance by: the header is derived from the
   *     dataset's item count, which lags a fresh push, so it can fall below the items returned.
   *     Page by {@code getCount()}, as {@link com.apify.client.dataset.DatasetClient#iterateItems}
   *     does.
   */
  @Deprecated
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

  /**
   * Sets the raw value of the scanned-rows header.
   *
   * @deprecated see {@link #getScannedCount()}
   */
  @Deprecated
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
