package com.apify.client;

import com.apify.client.internal.ListOptionsLike;
import com.apify.client.internal.QueryParams;

/**
 * Options for the storage collection list endpoints ({@code GET /v2/datasets}, {@code
 * /v2/key-value-stores}, {@code /v2/request-queues}), which add {@code unnamed} and {@code
 * ownership} filters on top of the standard pagination.
 */
public final class StorageListOptions implements ListOptionsLike {
  private Long offset;
  private Long limit;
  private Boolean desc;
  private Boolean unnamed;
  private String ownership;

  /** Number of items to skip from the beginning of the list. */
  public StorageListOptions offset(Long offset) {
    this.offset = offset;
    return this;
  }

  /**
   * Maximum number of items to return. Sent verbatim by {@code list(...)} (so {@code 0} returns
   * zero items); in {@code iterate(...)} a non-positive/zero {@code limit} means "no cap" (all
   * items).
   */
  public StorageListOptions limit(Long limit) {
    this.limit = limit;
    return this;
  }

  /** If {@code true}, return items newest-first. */
  public StorageListOptions desc(Boolean desc) {
    this.desc = desc;
    return this;
  }

  /** If {@code true}, include unnamed storages in the result. */
  public StorageListOptions unnamed(Boolean unnamed) {
    this.unnamed = unnamed;
    return this;
  }

  /** Filter by ownership (e.g. {@code "OWNED"} / {@code "ACCESSIBLE"}). */
  public StorageListOptions ownership(String ownership) {
    this.ownership = ownership;
    return this;
  }

  @Override
  public Long offsetValue() {
    return offset;
  }

  @Override
  public Long limitValue() {
    return limit;
  }

  @Override
  public void apply(QueryParams q) {
    q.addLong("offset", offset).addLong("limit", limit);
    applyFilters(q);
  }

  /**
   * Applies every filter except {@code offset}/{@code limit}, which the iterator drives per page.
   */
  @Override
  public void applyFilters(QueryParams q) {
    q.addBool("desc", desc).addBool("unnamed", unnamed).addString("ownership", ownership);
  }
}
