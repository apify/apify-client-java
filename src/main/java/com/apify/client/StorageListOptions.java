package com.apify.client;

/**
 * Options for the storage collection list endpoints ({@code GET /v2/datasets}, {@code
 * /v2/key-value-stores}, {@code /v2/request-queues}), which add {@code unnamed} and {@code
 * ownership} filters on top of the standard pagination.
 */
public final class StorageListOptions {
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

  /** Maximum number of items to return. */
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

  void apply(QueryParams q) {
    q.addLong("offset", offset)
        .addLong("limit", limit)
        .addBool("desc", desc)
        .addBool("unnamed", unnamed)
        .addString("ownership", ownership);
  }
}
