package com.apify.client.actor;

import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A client for a specific Actor version ({@code GET/PUT/DELETE
 * /v2/actors/{actorId}/versions/{versionNumber}}).
 */
public final class ActorVersionClient {
  private final HttpClientCore http;
  private final ResourceContext ctx;
  private final String versionUrl;

  ActorVersionClient(HttpClientCore http, String actorUrl, String versionNumber) {
    this.http = http;
    this.ctx = ResourceContext.single(http, actorUrl, "versions", versionNumber);
    this.versionUrl = ctx.subUrl("");
  }

  /** Fetches the version, or empty if it does not exist. */
  public CompletableFuture<Optional<ActorVersion>> get() {
    return ctx.getResource("", new QueryParams(), ActorVersion.class);
  }

  /** Updates the version with the given fields and returns the updated object. */
  public CompletableFuture<ActorVersion> update(Object newFields) {
    return ctx.updateResource("", newFields, ActorVersion.class);
  }

  /** Deletes the version. */
  public CompletableFuture<Void> delete() {
    return ctx.deleteResource("");
  }

  /** A client for a specific environment variable of this version. */
  public ActorEnvVarClient envVar(String name) {
    return new ActorEnvVarClient(http, versionUrl, name);
  }

  /** A client for this version's environment variable collection. */
  public ActorEnvVarCollectionClient envVars() {
    return new ActorEnvVarCollectionClient(http, versionUrl);
  }
}
