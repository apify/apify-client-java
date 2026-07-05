package com.apify.client;

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

  /** Creates a new environment variable. */
  public ActorEnvVar create(ActorEnvVar envVar) {
    return ctx.createResource(new QueryParams(), envVar, ActorEnvVar.class);
  }
}
