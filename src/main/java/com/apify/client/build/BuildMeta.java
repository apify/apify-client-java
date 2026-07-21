package com.apify.client.build;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Metadata about how a {@link Build} was initiated. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BuildMeta {
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
