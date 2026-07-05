package com.apify.client;

/** Options for {@link ActorCollectionClient#list(ActorListOptions)}. */
public final class ActorListOptions {
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

  /** Maximum number of Actors to return. */
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

  void apply(QueryParams q) {
    q.addLong("offset", offset)
        .addLong("limit", limit)
        .addBool("desc", desc)
        .addBool("my", my)
        .addString("sortBy", sortBy);
  }
}
