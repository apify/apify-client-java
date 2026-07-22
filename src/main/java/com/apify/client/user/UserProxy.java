package com.apify.client.user;

import com.apify.client.ApifyResource;
import java.util.Collections;
import java.util.List;

/** A {@link User}'s Apify Proxy credentials and available proxy groups. */
public final class UserProxy extends ApifyResource {
  private String password;
  private List<ProxyGroup> groups = List.of();

  /** The password used to authenticate with Apify Proxy. */
  public String getPassword() {
    return password;
  }

  /** The proxy groups available to this user (unmodifiable, never {@code null}). */
  public List<ProxyGroup> getGroups() {
    return groups == null ? List.of() : Collections.unmodifiableList(groups);
  }
}
