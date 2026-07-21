package com.apify.client.user;

import com.apify.client.ApifyResource;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * An Apify user account. Private account details (available only for {@code me}) are typed below
 * where reasonably finite; {@link #getEffectivePlatformFeatures()} stays raw JSON (a fixed set of
 * feature-name keys, each with its own limits sub-object) and any other unmodelled field is still
 * reachable via {@link #getExtra()}.
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

  /** The user's email address. Only present for {@code me}. */
  public String getEmail() {
    return email;
  }

  /** The user's Apify Proxy credentials and available proxy groups. Only present for {@code me}. */
  public UserProxy getProxy() {
    return proxy;
  }

  /** The user's subscription plan. Only present for {@code me}. */
  public UserPlan getPlan() {
    return plan;
  }

  /**
   * The platform features actually available to the user right now (accounting for plan limits and
   * current usage), as raw JSON. Only present for {@code me}.
   */
  public JsonNode getEffectivePlatformFeatures() {
    return effectivePlatformFeatures;
  }

  /** When the user account was created. Only present for {@code me}. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Whether the user is on a paid plan. Only present for {@code me}. */
  public Boolean getIsPaying() {
    return isPaying;
  }
}
