package com.apify.client;

import java.util.Optional;

/**
 * A client for a single environment variable ({@code GET/PUT/DELETE
 * /v2/actors/{actorId}/versions/{versionNumber}/env-vars/{name}}).
 */
public final class ActorEnvVarClient {
  private final ResourceContext ctx;

  ActorEnvVarClient(HttpClientCore http, String versionUrl, String name) {
    this.ctx = ResourceContext.single(http, versionUrl, "env-vars", name);
  }

  /** Fetches the environment variable, or empty if it does not exist. */
  public Optional<ActorEnvVar> get() {
    return ctx.getResource("", new QueryParams(), ActorEnvVar.class);
  }

  /** Updates the environment variable and returns the updated object. */
  public ActorEnvVar update(ActorEnvVar envVar) {
    return ctx.updateResource("", envVar, ActorEnvVar.class);
  }

  /** Deletes the environment variable. */
  public void delete() {
    ctx.deleteResource("");
  }
}
