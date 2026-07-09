package com.apify.client;

/**
 * How an Actor run was started (the API's {@code RunOrigin} / {@code meta.origin}), used to filter
 * the "last" run via {@link LastRunOptions#origin(RunOrigin)}.
 *
 * <p>This enum is write-only: it is only ever turned into the {@code origin} query parameter via
 * {@link #getWireValue()}, never deserialized, so it needs no Jackson annotations and no {@code
 * UNKNOWN} read sentinel.
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
  MCP("MCP");

  private final String wireValue;

  RunOrigin(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The value sent in the {@code origin} query parameter. */
  public String getWireValue() {
    return wireValue;
  }
}
