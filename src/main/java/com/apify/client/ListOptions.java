package com.apify.client;

/**
 * The standard offset/limit pagination shared by most {@code list} endpoints (builds, runs, tasks,
 * schedules, webhooks, Actor versions). All fields are optional; leave one unset to use the API
 * default.
 */
public final class ListOptions {
  private Long offset;
  private Long limit;
  private Boolean desc;

  /** Number of items to skip from the beginning of the list. */
  public ListOptions offset(Long offset) {
    this.offset = offset;
    return this;
  }

  /** Maximum number of items to return. */
  public ListOptions limit(Long limit) {
    this.limit = limit;
    return this;
  }

  /** If {@code true}, return items newest-first. */
  public ListOptions desc(Boolean desc) {
    this.desc = desc;
    return this;
  }

  void apply(QueryParams q) {
    q.addLong("offset", offset).addLong("limit", limit).addBool("desc", desc);
  }
}
