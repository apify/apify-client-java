package com.apify.client.internal;

import com.apify.client.PaginationList;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared {@code list}/{@code iterate} behavior for an offset/limit-paginated collection endpoint,
 * generic over the item type {@code T} and its options type {@code O}. Follows the same shape as
 * {@code com.apify.client.webhook.AbstractWebhookCollectionClient} (which predates the generic
 * {@code Class<T>} witness {@link ResourceContext#listResource}/{@link
 * ResourceContext#iterateResource} take, and stays a separate, non-generic base since only one
 * concrete subclass pair needs it) but factors the item-class witness and the "no options ->
 * default options" fallback out so every other offset/limit-paginated collection client (Dataset,
 * KeyValueStore, RequestQueue, Actor, Schedule, Task, Build) can extend this one instead of
 * repeating the same three methods.
 *
 * <p>{@code RunCollectionClient} additionally takes a run-specific status filter alongside {@code
 * O}, so it does not extend this class directly; it still avoids duplicating the {@code
 * ctx}/item-class plumbing by building on the protected helpers here.
 */
public abstract class AbstractCollectionClient<T, O extends ListOptionsLike> {

  protected final ResourceContext ctx;
  private final Class<T> itemClass;
  private final Supplier<O> defaultOptions;

  protected AbstractCollectionClient(
      ResourceContext ctx, Class<T> itemClass, Supplier<O> defaultOptions) {
    this.ctx = ctx;
    this.itemClass = itemClass;
    this.defaultOptions = defaultOptions;
  }

  /** Lists the collection's items for one page. {@code options} may be {@code null} (defaults). */
  public PaginationList<T> list(O options) {
    O opts = options != null ? options : defaultOptions.get();
    QueryParams params = new QueryParams();
    opts.apply(params);
    return listWithParams(params);
  }

  /**
   * Returns a lazy iterator over the collection. The options' {@code limit} caps the total number
   * yielded ({@code null} or non-positive = all); {@code chunkSize} is the per-request page size
   * ({@code null} = server default).
   */
  public Iterator<T> iterate(O options) {
    return iterate(options, null);
  }

  /** As {@link #iterate(ListOptionsLike)}, but {@code chunkSize} sets the per-request page size. */
  public Iterator<T> iterate(O options, Long chunkSize) {
    O opts = options != null ? options : defaultOptions.get();
    return iterateWithFilters(opts.limitValue(), chunkSize, opts.offsetValue(), opts::applyFilters);
  }

  /**
   * Fetches one page using an already-built {@code params} (offset/limit plus filters). Exposed for
   * subclasses (e.g. {@code RunCollectionClient}) that need to merge in an extra filter ({@code
   * options} alone cannot express) before delegating to the same {@code ctx}/item-class.
   */
  protected final PaginationList<T> listWithParams(QueryParams params) {
    return ctx.listResource("", params, itemClass);
  }

  /**
   * As {@link #listWithParams}, but for lazy iteration: {@code applyFilters} supplies every filter
   * except {@code offset}/{@code limit}, which the iterator drives per page.
   */
  protected final Iterator<T> iterateWithFilters(
      Long limit, Long chunkSize, Long offset, Consumer<QueryParams> applyFilters) {
    return ctx.iterateResource("", limit, chunkSize, offset, applyFilters, itemClass);
  }

  /**
   * The default options instance used by {@link #iterate(ListOptionsLike)} when passed {@code
   * null}.
   */
  protected final O defaultOptions() {
    return defaultOptions.get();
  }
}
