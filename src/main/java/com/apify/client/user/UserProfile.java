package com.apify.client.user;

import com.apify.client.ApifyResource;
import com.apify.client.internal.NormalizedUrlDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/** The public profile of a {@link User}. */
public final class UserProfile extends ApifyResource {
  private String bio;
  private String readme;
  private String name;

  @JsonDeserialize(using = NormalizedUrlDeserializer.class)
  private String pictureUrl;

  private String githubUsername;

  @JsonDeserialize(using = NormalizedUrlDeserializer.class)
  private String websiteUrl;

  private String twitterUsername;

  /** A short biography, if set. */
  public String getBio() {
    return bio;
  }

  /** Markdown README shown on the user's public profile page, if set. */
  public String getReadme() {
    return readme;
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
