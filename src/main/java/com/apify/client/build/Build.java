package com.apify.client.build;

import com.apify.client.ApifyResource;
import com.apify.client.internal.Statuses;
import java.time.Instant;

/** A single build of an Actor. */
public final class Build extends ApifyResource {
  private String id;
  private String actId;
  private String userId;
  private String status;
  private Instant startedAt;
  private Instant finishedAt;
  private String buildNumber;
  private String imageDigest;
  private BuildActVersion actVersion;
  private ActorDefinition actorDefinition;
  private BuildMeta meta;
  private BuildStats stats;
  private BuildOptions options;
  private BuildUsage usage;
  private BuildUsage usageUsd;
  private Double usageTotalUsd;
  private String changelog;
  private Integer exitCode;
  private String gitCommitId;
  private String gitBranchName;
  private String createdByOrganizationMemberUserId;

  /** The unique build ID. */
  public String getId() {
    return id;
  }

  /** The ID of the Actor this build belongs to. */
  public String getActId() {
    return actId;
  }

  /** The ID of the user who started the build. */
  public String getUserId() {
    return userId;
  }

  /**
   * The current build status. One of the eight {@code ActorJobStatus} values: {@code READY}, {@code
   * RUNNING}, {@code SUCCEEDED}, {@code FAILED}, {@code TIMING-OUT}, {@code TIMED-OUT}, {@code
   * ABORTING}, {@code ABORTED}.
   */
  public String getStatus() {
    return status;
  }

  /** When the build started. */
  public Instant getStartedAt() {
    return startedAt;
  }

  /** When the build finished (absent while still building). */
  public Instant getFinishedAt() {
    return finishedAt;
  }

  /** The human-readable build number (e.g. {@code "0.1.2"}). */
  public String getBuildNumber() {
    return buildNumber;
  }

  /**
   * Digest of the built Docker image manifest, without the {@code sha256:} prefix. Compare the
   * digests of two builds to find out whether their image contents differ. {@code null} if the
   * digest is not available.
   */
  public String getImageDigest() {
    return imageDigest;
  }

  /**
   * A snapshot of the Actor version this build was created from. Its {@link
   * BuildActVersion#getBuildTag()} is {@code null} for a version without a build tag.
   */
  public BuildActVersion getActVersion() {
    return actVersion;
  }

  /**
   * The definition of the Actor this build was created from, as stored in its {@code
   * .actor/actor.json}. {@code null} if the build has none.
   */
  public ActorDefinition getActorDefinition() {
    return actorDefinition;
  }

  /** Metadata about how the build was initiated. */
  public BuildMeta getMeta() {
    return meta;
  }

  /** Runtime statistics for the build. */
  public BuildStats getStats() {
    return stats;
  }

  /** The configuration options actually applied to the build. */
  public BuildOptions getOptions() {
    return options;
  }

  /** Resource usage incurred by the build, per billable unit. */
  public BuildUsage getUsage() {
    return usage;
  }

  /** {@link #getUsage()}'s cost, in USD, per billable unit. */
  public BuildUsage getUsageUsd() {
    return usageUsd;
  }

  /** The build's total cost, in USD. */
  public Double getUsageTotalUsd() {
    return usageTotalUsd;
  }

  /** Whether the build has reached a terminal (finished) status. */
  public boolean isTerminal() {
    return Statuses.isTerminal(status);
  }

  /**
   * The changelog of the Actor version built.
   *
   * @deprecated The OpenAPI specification marks {@code Build.changelog} as deprecated.
   */
  @Deprecated
  public String getChangelog() {
    return changelog;
  }

  /** The exit code of the build process. */
  public Integer getExitCode() {
    return exitCode;
  }

  /** The Git commit the build was created from, for Git-sourced versions. */
  public String getGitCommitId() {
    return gitCommitId;
  }

  /** The Git branch the build was created from, for Git-sourced versions. */
  public String getGitBranchName() {
    return gitBranchName;
  }

  /** The organization member who started the build, when started on behalf of an organization. */
  public String getCreatedByOrganizationMemberUserId() {
    return createdByOrganizationMemberUserId;
  }
}
