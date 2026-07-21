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
    // Null-coalesce: Jackson binds directly to the (private) `items` field for deserialization
    // (see Json's FIELD/ANY visibility config), which bypasses setItems()'s List.copyOf() default
    // whenever the API response contains an explicit `"items": null`. Falling back to List.of()
    // here keeps the "never null" contract regardless of how the field was populated.
    return items == null ? List.of() : Collections.unmodifiableList(items);
  }

  // Setters used by the collection-listing and dataset-items paths, which build a page from a
  // parsed response (or, for dataset items, from response headers) one field at a time. These stay
  // public rather than package-private: DatasetClient (com.apify.client.dataset, the only caller
  // outside Jackson's own field-based deserialization) builds a page's metadata one header at a
  // time in listItems/downloadItems, and Java has no cross-package "friend" visibility - narrowing
  // this without a public setter would mean either moving that header-parsing logic into this
  // (root) package, which is a bigger surgery than this getter surface warrants, or introducing a
  // module-info split purely for this one class. Left as-is; a caller holding a page can mutate it
  // after the fact, which is a real but minor and pre-existing encapsulation gap, not a correctness
  // bug (this client never re-reads a page after constructing it).
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

  public void setItems(List<T> items) {
    // Copy defensively: items is set once by the client from a freshly-parsed/collected list, but
    // taking an immutable copy means a caller can never mutate this page's items afterwards by
    // mutating the list it passed in, and getItems() needs no wrapping to stay unmodifiable.
    this.items = List.copyOf(items);
  }
}
