package com.apify.client;

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

  /** Creates a new Actor. {@code actor} is any JSON-serializable Actor definition. */
  public Actor create(Object actor) {
    return ctx.createResource(new QueryParams(), actor, Actor.class);
  }
}
