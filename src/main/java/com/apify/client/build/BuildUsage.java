package com.apify.client.build;

import com.apify.client.ApifyResource;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resource-usage metrics for a {@link Build}, broken down by billable unit (only compute units
 * apply to builds). {@code null} when the build did not incur that kind of usage.
 *
 * <p>The API reports this key in {@code SCREAMING_SNAKE_CASE} ({@code "ACTOR_COMPUTE_UNITS"}),
 * mapped to its idiomatic camelCase Java name via {@code @JsonProperty}, matching {@link
 * com.apify.client.run.ActorRunUsage}.
 */
public final class BuildUsage extends ApifyResource {
  @JsonProperty("ACTOR_COMPUTE_UNITS")
  private Double actorComputeUnits;

  /** Compute units consumed by the build. */
  public Double getActorComputeUnits() {
    return actorComputeUnits;
  }
}
