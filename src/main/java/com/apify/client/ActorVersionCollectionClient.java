package com.apify.client;

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

  /** Creates a new Actor version. {@code version} is any JSON-serializable version definition. */
  public ActorVersion create(Object version) {
    return ctx.createResource(new QueryParams(), version, ActorVersion.class);
  }
}
