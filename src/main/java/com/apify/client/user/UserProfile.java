package com.apify.client.user;

import com.apify.client.ApifyResource;

/** The public profile of a {@link User}. */
public final class UserProfile extends ApifyResource {
  private String bio;
  private String name;
  private String pictureUrl;
  private String githubUsername;
  private String websiteUrl;
  private String twitterUsername;

  /** A short biography, if set. */
  public String getBio() {
    return bio;
  }

  /** The user's display name, if set. */
  public String getName() {
    return name;
  }

  /** URL of the user's profile picture, if set. */
  public String getPictureUrl() {
    return pictureUrl;
  }

  /** The user's linked GitHub username, if any. */
  public String getGithubUsername() {
    return githubUsername;
  }

  /** The user's personal website URL, if set. */
  public String getWebsiteUrl() {
    return websiteUrl;
  }

  /** The user's linked Twitter/X username, if any. */
  public String getTwitterUsername() {
    return twitterUsername;
  }
}
