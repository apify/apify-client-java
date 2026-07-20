package com.apify.client.actor;

import com.apify.client.ApifyResource;

/** An environment variable attached to an Actor version. */
public final class ActorEnvVar extends ApifyResource {
  private String name;
  private String value;
  private Boolean isSecret;

  public ActorEnvVar() {}

  public ActorEnvVar(String name, String value) {
    this.name = name;
    this.value = value;
  }

  /** The environment variable name. */
  public String getName() {
    return name;
  }

  public ActorEnvVar setName(String name) {
    this.name = name;
    return this;
  }

  /** The environment variable value. */
  public String getValue() {
    return value;
  }

  public ActorEnvVar setValue(String value) {
    this.value = value;
    return this;
  }

  /** Whether the value is stored as a secret. */
  public Boolean getIsSecret() {
    return isSecret;
  }

  public ActorEnvVar setIsSecret(Boolean isSecret) {
    this.isSecret = isSecret;
    return this;
  }
}
