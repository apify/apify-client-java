package com.apify.client.build;

import com.apify.client.ApifyResource;

/** A snapshot of the Actor version a {@link Build} was created from. */
public final class BuildActVersion extends ApifyResource {
  private String sourceType;
  private String buildTag;
  private String versionNumber;
  private String gitRepoUrl;

  /**
   * How the version's source was provided (e.g. {@code "SOURCE_FILES"}, {@code "GIT_REPO"}, {@code
   * "TARBALL"}, {@code "GITHUB_GIST"}, {@code "SOURCE_CODE"}).
   */
  public String getSourceType() {
    return sourceType;
  }

  /**
   * The build tag of the Actor version the build was created from. {@code null} when the version
   * has no build tag, in which case the build is tagged on success only if a tag was passed when
   * starting it.
   */
  public String getBuildTag() {
    return buildTag;
  }

  /** The version number (e.g. {@code "0.1"}). */
  public String getVersionNumber() {
    return versionNumber;
  }

  /** URL of the Git repository; present when the source type is {@code GIT_REPO}. */
  public String getGitRepoUrl() {
    return gitRepoUrl;
  }
}
