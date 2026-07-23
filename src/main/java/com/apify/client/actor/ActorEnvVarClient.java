package com.apify.client.actor;

import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
  public CompletableFuture<Optional<ActorEnvVar>> get() {
    return ctx.getResource("", new QueryParams(), ActorEnvVar.class);
  }

  /** Updates the environment variable and returns the updated object. */
  public CompletableFuture<ActorEnvVar> update(ActorEnvVar envVar) {
    return ctx.updateResource("", envVar, ActorEnvVar.class);
  }

  /** Deletes the environment variable. */
  public CompletableFuture<Void> delete() {
    return ctx.deleteResource("");
  }
}
