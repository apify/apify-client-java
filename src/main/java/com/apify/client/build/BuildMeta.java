package com.apify.client.build;

import com.apify.client.ApifyResource;

/** Metadata about how a {@link Build} was initiated. */
public final class BuildMeta extends ApifyResource {
  private String origin;
  private String clientIp;
  private String userAgent;

  /** What triggered the build (e.g. {@code "WEB"}, {@code "API"}). */
  public String getOrigin() {
    return origin;
  }

  /** The IP address of the client that started the build, if known. */
  public String getClientIp() {
    return clientIp;
  }

  /** The {@code User-Agent} of the client that started the build. */
  public String getUserAgent() {
    return userAgent;
  }
}
