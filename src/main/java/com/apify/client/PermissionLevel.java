package com.apify.client;

/**
 * The permission level an Actor run executes with, used to override the Actor's default via {@link
 * ActorStartOptions#forcePermissionLevel(PermissionLevel)}.
 *
 * <p>This enum is write-only: it is only ever turned into the {@code forcePermissionLevel} query
 * parameter via {@link #getWireValue()}, never deserialized, so it needs no Jackson annotations.
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
  public String getWireValue() {
    return wireValue;
  }
}
