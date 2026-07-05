package com.apify.client;

/** A single version of an Actor. */
public final class ActorVersion extends ApifyResource {
  private String versionNumber;
  private String sourceType;

  /** The version identifier (e.g. {@code "0.1"}). */
  public String getVersionNumber() {
    return versionNumber;
  }

  /** How the version's source is provided (e.g. {@code "SOURCE_FILES"}). */
  public String getSourceType() {
    return sourceType;
  }
}
