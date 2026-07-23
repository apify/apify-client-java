package com.apify.client.actor;

import com.apify.client.internal.AbstractCollectionClient;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;

/** A client for the Actor collection ({@code GET/POST /v2/actors}). */
public final class ActorCollectionClient extends AbstractCollectionClient<Actor, ActorListOptions> {

  public ActorCollectionClient(HttpClientCore http, String baseUrl) {
    super(
        ResourceContext.collection(http, baseUrl, ApiPaths.ACTORS),
        Actor.class,
        ActorListOptions::new);
  }

  /** Creates a new Actor. {@code actor} is any JSON-serializable Actor definition. */
  public Actor create(Object actor) {
    return ctx.createResource(new QueryParams(), actor, Actor.class);
  }
}
