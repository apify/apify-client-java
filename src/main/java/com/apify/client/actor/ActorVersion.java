package com.apify.client.actor;

import com.apify.client.ApifyResource;

/** A single version of an Actor. */
public final class ActorVersion extends ApifyResource {
  private String versionNumber;
  private String sourceType;
  private String tarballUrl;
  private String gitHubGistUrl;
  private Integer githubWebhookId;
  private Integer gitlabWebhookId;
  private String bitbucketWebhookId;

  /** The version identifier (e.g. {@code "0.1"}). */
  public String getVersionNumber() {
    return versionNumber;
  }

  /** How the version's source is provided (e.g. {@code "SOURCE_FILES"}). */
  public String getSourceType() {
    return sourceType;
  }

  /** The tarball URL the source is fetched from, for {@code TARBALL} versions. */
  public String getTarballUrl() {
    return tarballUrl;
  }

  /** The GitHub Gist URL the source is fetched from, for {@code GITHUB_GIST} versions. */
  public String getGitHubGistUrl() {
    return gitHubGistUrl;
  }

  /** The ID of the GitHub webhook that triggers builds on push, if configured. */
  public Integer getGithubWebhookId() {
    return githubWebhookId;
  }

  /** The ID of the GitLab webhook that triggers builds on push, if configured. */
  public Integer getGitlabWebhookId() {
    return gitlabWebhookId;
  }

  /** The ID of the Bitbucket webhook that triggers builds on push, if configured. */
  public String getBitbucketWebhookId() {
    return bitbucketWebhookId;
  }
}
