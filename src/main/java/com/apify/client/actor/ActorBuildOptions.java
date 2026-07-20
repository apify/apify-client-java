package com.apify.client.actor;

import com.apify.client.internal.QueryParams;

/** Configures {@link ActorClient#build(String, ActorBuildOptions)}. */
public final class ActorBuildOptions {
  private Boolean betaPackages;
  private String tag;
  private Boolean useCache;
  private Long waitForFinish;

  /** If {@code true}, use beta versions of Apify packages. */
  public ActorBuildOptions betaPackages(Boolean betaPackages) {
    this.betaPackages = betaPackages;
    return this;
  }

  /** The tag to apply to the build (e.g. {@code "latest"}). */
  public ActorBuildOptions tag(String tag) {
    this.tag = tag;
    return this;
  }

  /** Whether to use the Docker build cache (default true). */
  public ActorBuildOptions useCache(Boolean useCache) {
    this.useCache = useCache;
    return this;
  }

  /** Maximum seconds to wait server-side for the build (max 60). */
  public ActorBuildOptions waitForFinish(Long waitForFinish) {
    this.waitForFinish = waitForFinish;
    return this;
  }

  void apply(QueryParams q) {
    q.addBool("betaPackages", betaPackages)
        .addString("tag", tag)
        .addBool("useCache", useCache)
        .addLong("waitForFinish", waitForFinish);
  }
}
