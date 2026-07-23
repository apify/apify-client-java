package com.apify.client.actor;

import com.apify.client.ApifyResource;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** An Actor on the Apify platform. */
public final class Actor extends ApifyResource {
  private String id;
  private String userId;
  private String name;
  private String username;
  private String title;
  private String description;
  private boolean isPublic;
  private Instant createdAt;
  private Instant modifiedAt;
  private ActorStats stats;
  private List<ActorVersion> versions = List.of();
  private List<JsonNode> pricingInfos = List.of();
  private ActorDefaultRunOptions defaultRunOptions;
  private JsonNode taggedBuilds;
  private Boolean isDeprecated;
  private String deploymentKey;
  private String seoTitle;
  private String seoDescription;
  private List<String> categories = List.of();
  private ActorStandby actorStandby;
  private String actorPermissionLevel;

  /** The unique Actor ID. */
  public String getId() {
    return id;
  }

  /** The ID of the user who owns the Actor. */
  public String getUserId() {
    return userId;
  }

  /** The technical name of the Actor (used in API paths). */
  public String getName() {
    return name;
  }

  /** The username of the Actor's owner. */
  public String getUsername() {
    return username;
  }

  /** The human-readable title shown in the UI. */
  public String getTitle() {
    return title;
  }

  /** A description of what the Actor does. */
  public String getDescription() {
    return description;
  }

  /** Whether the Actor is publicly available in Apify Store. */
  public boolean isPublic() {
    return isPublic;
  }

  /** When the Actor was created. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** When the Actor was last modified. */
  public Instant getModifiedAt() {
    return modifiedAt;
  }

  /** Usage and run statistics for the Actor. */
  public ActorStats getStats() {
    return stats;
  }

  /** All versions of this Actor (unmodifiable, never {@code null}). */
  public List<ActorVersion> getVersions() {
    // Null-coalesce: Jackson binds directly to the (private) `versions` field for deserialization,
    // which bypasses the `= List.of()` field initializer whenever the API response contains an
    // explicit `"versions": null`.
    return versions == null ? List.of() : Collections.unmodifiableList(versions);
  }

  /**
   * Pricing information for pay-per-result or pay-per-event Actors, as raw JSON (shape depends on
   * the pricing model; unmodifiable, never {@code null}).
   */
  public List<JsonNode> getPricingInfos() {
    return pricingInfos == null ? List.of() : Collections.unmodifiableList(pricingInfos);
  }

  /** Default configuration options applied to this Actor's runs unless overridden. */
  public ActorDefaultRunOptions getDefaultRunOptions() {
    return defaultRunOptions;
  }

  /**
   * The build tags mapped to specific build numbers/ids for this Actor (e.g. {@code "latest"}), as
   * raw JSON since the keys are dynamic tag names, not a fixed set of fields.
   */
  public JsonNode getTaggedBuilds() {
    return taggedBuilds;
  }

  /** Whether the Actor is deprecated and should no longer be used. */
  public Boolean getIsDeprecated() {
    return isDeprecated;
  }

  /** The deployment key used for automated deployments to this Actor. */
  public String getDeploymentKey() {
    return deploymentKey;
  }

  /** An SEO-optimized title for the Actor's public Store page. */
  public String getSeoTitle() {
    return seoTitle;
  }

  /** An SEO-optimized description for the Actor's public Store page. */
  public String getSeoDescription() {
    return seoDescription;
  }

  /**
   * Categories the Actor belongs to (e.g. {@code "ECOMMERCE"}, {@code "SCRAPING"}; unmodifiable,
   * never {@code null}).
   */
  public List<String> getCategories() {
    return categories == null ? List.of() : Collections.unmodifiableList(categories);
  }

  /** Standby-mode configuration for this Actor, if standby mode has ever been configured. */
  public ActorStandby getActorStandby() {
    return actorStandby;
  }

  /** The permission level of the Actor on the Apify platform (e.g. {@code "OWNER"}). */
  public String getActorPermissionLevel() {
    return actorPermissionLevel;
  }
}
