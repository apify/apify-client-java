package com.apify.client.actor;

import com.apify.client.PaginationList;
import com.apify.client.QueryParams;
import com.apify.client.ResourceContext;
import com.apify.client.http.HttpClientCore;
import java.util.Iterator;

/**
 * A client for an Actor version's environment variable collection ({@code GET/POST
 * /v2/actors/{actorId}/versions/{versionNumber}/env-vars}).
 */
public final class ActorEnvVarCollectionClient {
  private final ResourceContext ctx;

  ActorEnvVarCollectionClient(HttpClientCore http, String versionUrl) {
    this.ctx = ResourceContext.collection(http, versionUrl, "env-vars");
  }

  /** Lists the version's environment variables. */
  public PaginationList<ActorEnvVar> list() {
    return ctx.listResource("", new QueryParams(), ActorEnvVar.class);
  }

  /**
   * Returns an iterator over the version's environment variables. The env-var collection is not
   * paginated (the API returns every variable in one response), so this iterates a single fetched
   * page; the method exists for API consistency with the other collection clients.
   */
  public Iterator<ActorEnvVar> iterate() {
    return list().getItems().iterator();
  }

  /** Creates a new environment variable. */
  public ActorEnvVar create(ActorEnvVar envVar) {
    return ctx.createResource(new QueryParams(), envVar, ActorEnvVar.class);
  }
}
