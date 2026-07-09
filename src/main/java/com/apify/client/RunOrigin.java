package com.apify.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How an Actor run was started (the API's {@code RunOrigin} / {@code meta.origin}).
 *
 * <p>{@link #UNKNOWN} is returned for any origin value the API introduces that this client version
 * does not yet recognise, so deserialization never fails on a newer server.
 */
public enum RunOrigin {
  /** Started from the Actor's development console. */
  DEVELOPMENT("DEVELOPMENT"),
  /** Started manually from the Apify Console web UI. */
  WEB("WEB"),
  /** Started programmatically through the API. */
  API("API"),
  /** Started by a schedule. */
  SCHEDULER("SCHEDULER"),
  /** Started as part of a test. */
  TEST("TEST"),
  /** Started by a webhook. */
  WEBHOOK("WEBHOOK"),
  /** Started by another Actor. */
  ACTOR("ACTOR"),
  /** Started by a continuous-integration pipeline. */
  CI("CI"),
  /** Started from the Apify command-line interface. */
  CLI("CLI"),
  /** Started from an Actor Standby request. */
  STANDBY("STANDBY"),
  /** Started through the Model Context Protocol integration. */
  MCP("MCP"),
  /** An origin value the API returned that this client version does not recognise. */
  UNKNOWN(null);

  private final String wireValue;

  RunOrigin(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The value used on the wire; {@code null} for {@link #UNKNOWN}. */
  @JsonValue
  public String getWireValue() {
    return wireValue;
  }

  /** Maps a wire value to its constant, or {@link #UNKNOWN} for an unrecognised non-null value. */
  @JsonCreator
  public static RunOrigin fromWire(String value) {
    if (value == null) {
      return null;
    }
    for (RunOrigin origin : values()) {
      if (value.equals(origin.wireValue)) {
        return origin;
      }
    }
    return UNKNOWN;
  }
}
