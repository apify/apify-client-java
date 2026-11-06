package com.apify.client.build;

import com.apify.client.ApifyResource;
import com.apify.client.actor.SourceCodeFile;
import java.util.Collections;
import java.util.List;

/** A snapshot of the Actor version a {@link Build} was created from. */
public final class BuildActVersion extends ApifyResource {
  private String sourceType;
  private String buildTag;
  private String versionNumber;
  private String gitRepoUrl;
  private List<SourceCodeFile> sourceFiles;
  private String actorPath;

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

  /**
   * The inline source files; non-empty only when the source type is {@code SOURCE_FILES}, an empty
   * list otherwise.
   */
  public List<SourceCodeFile> getSourceFiles() {
    return sourceFiles == null ? List.of() : Collections.unmodifiableList(sourceFiles);
  }

  /**
   * The Actor's directory inside the uploaded source, present when the version sets it. It applies
   * when the source type is {@code SOURCE_FILES} or {@code TARBALL}. On a {@code TARBALL} version,
   * only users with write access to the Actor get it.
   */
  public String getActorPath() {
    return actorPath;
  }
}
