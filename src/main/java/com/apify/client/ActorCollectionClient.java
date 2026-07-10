package com.apify.client;

import java.util.Iterator;

/** A client for the Actor collection ({@code GET/POST /v2/actors}). */
public final class ActorCollectionClient {
  private final ResourceContext ctx;

  ActorCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, "actors");
  }

  /** Lists the account's Actors. */
  public PaginationList<Actor> list(ActorListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, Actor.class);
  }

  /**
   * Returns a lazy iterator over the account's Actors, fetching pages on demand. The options'
   * {@code limit} caps the total number of Actors yielded ({@code null} = all); {@code chunkSize}
   * is the per-request page size ({@code null} = server default).
   */
  public Iterator<Actor> iterate(ActorListOptions options, Long chunkSize) {
    ActorListOptions opts = options != null ? options : new ActorListOptions();
    return ctx.iterateResource(
        "", opts.limitValue(), chunkSize, opts.offsetValue(), opts::applyFilters, Actor.class);
  }

  /** Creates a new Actor. {@code actor} is any JSON-serializable Actor definition. */
  public Actor create(Object actor) {
    return ctx.createResource(new QueryParams(), actor, Actor.class);
  }
}
