package com.apify.client.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

/** A {@link User}'s Apify Proxy credentials and available proxy groups. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class UserProxy {
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
