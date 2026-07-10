package com.apify.client;

import java.util.Iterator;

/** A client for an Actor's version collection ({@code GET/POST /v2/actors/{actorId}/versions}). */
public final class ActorVersionCollectionClient {
  private final ResourceContext ctx;

  ActorVersionCollectionClient(HttpClientCore http, String actorUrl) {
    this.ctx = ResourceContext.collection(http, actorUrl, "versions");
  }

  /** Lists the Actor's versions. */
  public PaginationList<ActorVersion> list(ListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, ActorVersion.class);
  }

  /**
   * Returns a lazy iterator over the Actor's versions. The options' {@code limit} caps the total
   * number yielded ({@code null} = all); {@code chunkSize} is the per-request page size ({@code
   * null} = server default).
   */
  public Iterator<ActorVersion> iterate(ListOptions options, Long chunkSize) {
    ListOptions opts = options != null ? options : new ListOptions();
    return ctx.iterateResource(
        "",
        opts.limitValue(),
        chunkSize,
        opts.offsetValue(),
        opts::applyFilters,
        ActorVersion.class);
  }

  /** Creates a new Actor version. {@code version} is any JSON-serializable version definition. */
  public ActorVersion create(Object version) {
    return ctx.createResource(new QueryParams(), version, ActorVersion.class);
  }
}
