package com.apify.client;

import java.util.Iterator;
import java.util.List;

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
   * Returns a lazy iterator over the Actor's versions.
   *
   * <p>{@code GET /v2/actors/{actorId}/versions} is <em>not</em> offset/limit paginated: it takes
   * no pagination parameters and returns the full version list in a single {@code {total, items}}
   * response (the server ignores {@code offset}). This iterates that one fetched page, so draining
   * the iterator always terminates and never re-yields a version. (Routing it through the offset/
   * limit paging engine would loop forever, since the server returns the same non-empty page at
   * every offset — this is why the sibling non-paginated {@code env-vars} collection is also a
   * single-fetch iterator.) The options' {@code limit} still caps the number yielded ({@code null}
   * or non-positive = all); {@code offset} has no effect (the server ignores it) and there is no
   * page size to tune.
   */
  public Iterator<ActorVersion> iterate(ListOptions options) {
    ListOptions opts = options != null ? options : new ListOptions();
    List<ActorVersion> items = list(opts).getItems();
    Long limit = opts.limitValue();
    if (limit != null && limit > 0 && items.size() > limit) {
      items = items.subList(0, (int) (long) limit);
    }
    return items.iterator();
  }

  /** Creates a new Actor version. {@code version} is any JSON-serializable version definition. */
  public ActorVersion create(Object version) {
    return ctx.createResource(new QueryParams(), version, ActorVersion.class);
  }
}
