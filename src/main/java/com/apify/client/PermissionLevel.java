package com.apify.client;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The permission level an Actor run executes with, used to override the Actor's default via {@link
 * ActorStartOptions#forcePermissionLevel(PermissionLevel)}.
 */
public enum PermissionLevel {
  /** The run is restricted to a limited set of permissions. */
  LIMITED_PERMISSIONS("LIMITED_PERMISSIONS"),
  /** The run has the Actor's full set of permissions. */
  FULL_PERMISSIONS("FULL_PERMISSIONS");

  private final String wireValue;

  PermissionLevel(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The value sent in the {@code forcePermissionLevel} query parameter. */
  @JsonValue
  public String getWireValue() {
    return wireValue;
  }
}
