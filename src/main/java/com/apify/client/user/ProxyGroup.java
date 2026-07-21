package com.apify.client.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A group of proxies available to a {@link User}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ProxyGroup {
  private String name;
  private String description;
  private Long availableCount;

  /** The proxy group's identifier (used as the {@code groups} value when configuring a proxy). */
  public String getName() {
    return name;
  }

  /** A human-readable description of the proxy group. */
  public String getDescription() {
    return description;
  }

  /** The number of proxies currently available in this group. */
  public Long getAvailableCount() {
    return availableCount;
  }
}
