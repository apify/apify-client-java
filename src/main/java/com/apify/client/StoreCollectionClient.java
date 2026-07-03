package com.apify.client;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** A client for browsing the Apify Store ({@code GET /v2/store}). */
public final class StoreCollectionClient {
  private final ResourceContext ctx;

  StoreCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, "store");
  }

  /** Returns a single page of Store Actors matching the options. */
  public PaginationList<ActorStoreListItem> list(StoreListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, ActorStoreListItem.class);
  }

  /**
   * Returns a lazy iterator over all Store Actors matching the options, fetching pages on demand.
   * The options' {@code limit} (if set) is used as the per-page size.
   */
  public Iterator<ActorStoreListItem> iterate(StoreListOptions options) {
    return new StoreIterator(options);
  }

  /** Lazily iterates over Apify Store Actors, fetching one page at a time. */
  private final class StoreIterator implements Iterator<ActorStoreListItem> {
    private final StoreListOptions options;
    private List<ActorStoreListItem> buffer = List.of();
    private int pos;
    private long offset;
    private long total;
    private boolean exhausted;

    StoreIterator(StoreListOptions options) {
      // Copy so paging state stays internal: the caller's instance is never mutated (safe to reuse
      // or iterate twice), and its initial offset is honored as the starting page.
      this.options = options.copy();
      Long initialOffset = this.options.offsetValue();
      this.offset = initialOffset != null ? initialOffset : 0;
    }

    @Override
    public boolean hasNext() {
      while (pos >= buffer.size()) {
        if (exhausted) {
          return false;
        }
        fetchPage();
      }
      return true;
    }

    @Override
    public ActorStoreListItem next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return buffer.get(pos++);
    }

    private void fetchPage() {
      options.offsetInternal(offset);
      PaginationList<ActorStoreListItem> page = list(options);
      buffer = page.getItems();
      pos = 0;
      total = page.getTotal();
      offset += page.getItems().size();
      if (page.getItems().isEmpty() || offset >= total) {
        exhausted = true;
      }
    }
  }
}
