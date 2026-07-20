package com.apify.client.internal;

/**
 * The common shape every offset/limit-paginated {@code list(...)} options type ({@code
 * com.apify.client.ListOptions}, {@code StorageListOptions}, {@code ActorListOptions}, ...)
 * implements, so {@link AbstractCollectionClient} can build query params and drive iteration
 * generically across all of them.
 */
public interface ListOptionsLike {

  /** Number of items to skip from the beginning of the list, or {@code null} for the default. */
  Long offsetValue();

  /** Maximum number of items to return, or {@code null} for the default/unbounded. */
  Long limitValue();

  /** Applies every query parameter, including {@code offset}/{@code limit}. */
  void apply(QueryParams q);

  /**
   * Applies every filter except {@code offset}/{@code limit}, which a paging iterator drives per
   * page rather than taking from the options object.
   */
  void applyFilters(QueryParams q);
}
