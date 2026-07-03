package com.apify.client;

/**
 * An Apify user account. Private account details for {@code me} are available via {@link
 * #getExtra()}.
 */
public final class User extends ApifyResource {
  private String id;
  private String username;

  /** The unique user ID. */
  public String getId() {
    return id;
  }

  /** The user's username. */
  public String getUsername() {
    return username;
  }
}
