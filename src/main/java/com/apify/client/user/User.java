package com.apify.client.user;

import com.apify.client.ApifyResource;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * An Apify user account. The public fields are always set. The private fields ({@link #getEmail()},
 * {@link #getProxy()}, {@link #getPlan()}, {@link #getEffectivePlatformFeatures()}, {@link
 * #getCreatedAt()} and {@link #getIsPaying()}) are set for {@code me}, and for another user only
 * when the API token belongs to that user or impersonates it. {@link #getPlan()}, {@link
 * #getEmail()} and {@link #getProfile()} are omitted when the request is made from an Actor run. A
 * request authenticated with an Apify Proxy password ({@code PROXY_PASSWORD} or {@code
 * ACTOR_RUN_PROXY_PASSWORD}) gets reduced data on both endpoints: only {@link #getPlan()} (with
 * just {@link UserPlan#getAvailableProxyGroups()}), {@link #getEffectivePlatformFeatures()}, and
 * {@link #getIsPaying()} are set, and {@link #getId()} and {@link #getUsername()} are {@code null}.
 * {@link #getEffectivePlatformFeatures()} stays raw JSON (a fixed set of feature-name keys, each
 * with its own limits sub-object), and any other unmodeled field is still reachable via {@link
 * #getExtra()}.
 */
public final class User extends ApifyResource {
  private String id;
  private String username;
  private UserProfile profile;
  private String email;
  private UserProxy proxy;
  private UserPlan plan;
  private JsonNode effectivePlatformFeatures;
  private Instant createdAt;
  private Boolean isPaying;

  /** The unique user ID. */
  public String getId() {
    return id;
  }

  /** The user's username. */
  public String getUsername() {
    return username;
  }

  /** The user's public profile. */
  public UserProfile getProfile() {
    return profile;
  }

  /** The user's email address. Private field; see the class description. */
  public String getEmail() {
    return email;
  }

  /**
   * The user's Apify Proxy credentials and available proxy groups. Private field; see the class
   * description.
   */
  public UserProxy getProxy() {
    return proxy;
  }

  /** The user's subscription plan. Private field; see the class description. */
  public UserPlan getPlan() {
    return plan;
  }

  /**
   * The platform features actually available to the user right now (accounting for plan limits and
   * current usage), as raw JSON. Private field; see the class description.
   */
  public JsonNode getEffectivePlatformFeatures() {
    return effectivePlatformFeatures;
  }

  /** When the user account was created. Private field; see the class description. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Whether the user is on a paid plan. Private field; see the class description. */
  public Boolean getIsPaying() {
    return isPaying;
  }
}
