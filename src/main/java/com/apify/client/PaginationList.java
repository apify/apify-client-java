package com.apify.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PaginationList<T> {

  private long total;
  private long offset;
  private long limit;
  private long count;
  private boolean desc;
  private List<T> items = List.of();

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

  /** The items of this page (never {@code null}; unmodifiable). */
  public List<T> getItems() {
    return Collections.unmodifiableList(items);
  }

  // These setters are not intended for application use — the API always returns fully-populated
  // pages. They are public only because com.apify.client.dataset.DatasetClient (a different
  // package, post package-split) builds a page from response headers for the dataset-items
  // endpoint, which reports pagination metadata via headers rather than a JSON envelope.

  /** Not for application use; see the class-level note above. */
  public void setTotal(long total) {
    this.total = total;
  }

  /** Not for application use; see the class-level note above. */
  public void setOffset(long offset) {
    this.offset = offset;
  }

  /** Not for application use; see the class-level note above. */
  public void setLimit(long limit) {
    this.limit = limit;
  }

  /** Not for application use; see the class-level note above. */
  public void setCount(long count) {
    this.count = count;
  }

  /** Not for application use; see the class-level note above. */
  public void setDesc(boolean desc) {
    this.desc = desc;
  }

  /**
   * Not for application use; see the class-level note above. Defensively copies {@code items} so a
   * caller-held reference to the original list cannot mutate this page afterwards (the constructor
   * is public only for the same cross-package-wiring reason as the other setters here).
   */
  public void setItems(List<T> items) {
    this.items = items == null ? List.of() : List.copyOf(items);
  }
}
