package com.apify.client;

import com.apify.client.internal.ListOptionsLike;
import com.apify.client.internal.QueryParams;

/**
 * The standard offset/limit pagination shared by most {@code list} endpoints (builds, runs, tasks,
 * schedules, webhooks, Actor versions). All fields are optional; leave one unset to use the API
 * default.
 */
public final class ListOptions implements ListOptionsLike {
  private Long offset;
  private Long limit;
  private Boolean desc;

  /** Number of items to skip from the beginning of the list. */
  public ListOptions offset(Long offset) {
    this.offset = offset;
    return this;
  }

  /**
   * Maximum number of items to return. {@code list(...)} sends it as it is, so {@code 0} returns no
   * items; in {@code iterate(...)} a non-positive limit means no cap.
   */
  public ListOptions limit(Long limit) {
    this.limit = limit;
    return this;
  }

  /** If {@code true}, return items newest-first. */
  public ListOptions desc(Boolean desc) {
    this.desc = desc;
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
    q.addBool("desc", desc);
  }
}
