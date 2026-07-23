package com.apify.client.actor;

import com.apify.client.internal.ListOptionsLike;
import com.apify.client.internal.QueryParams;

/** Options for {@link ActorCollectionClient#list(ActorListOptions)}. */
public final class ActorListOptions implements ListOptionsLike {
  private Long offset;
  private Long limit;
  private Boolean desc;
  private Boolean my;
  private String sortBy;

  /** Number of Actors to skip. */
  public ActorListOptions offset(Long offset) {
    this.offset = offset;
    return this;
  }

  /**
   * Maximum number of Actors to return. Sent verbatim by {@code list(...)} (so {@code 0} returns
   * zero Actors); in {@code iterate(...)} a non-positive/zero {@code limit} means "no cap" (all).
   */
  public ActorListOptions limit(Long limit) {
    this.limit = limit;
    return this;
  }

  /** If {@code true}, return Actors newest-first. */
  public ActorListOptions desc(Boolean desc) {
    this.desc = desc;
    return this;
  }

  /** If {@code true}, return only Actors owned by the current user. */
  public ActorListOptions my(Boolean my) {
    this.my = my;
    return this;
  }

  /** The sort field (e.g. {@code "createdAt"}, {@code "stats.lastRunStartedAt"}). */
  public ActorListOptions sortBy(String sortBy) {
    this.sortBy = sortBy;
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
    q.addBool("desc", desc).addBool("my", my).addString("sortBy", sortBy);
  }
}
