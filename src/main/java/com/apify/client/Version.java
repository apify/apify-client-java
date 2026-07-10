package com.apify.client;

/**
 * Public version constants for the Apify Java client.
 *
 * <p>{@link #CLIENT_VERSION} is the semantic version of this library and {@link #API_SPEC_VERSION}
 * is the {@code info.version} of the Apify OpenAPI specification this client was generated and
 * verified against.
 */
public final class Version {

  /**
   * The semantic version of this client library (see <a href="https://semver.org/">SemVer</a>).
   * Changes to the public interface other than additive ones are considered breaking changes.
   */
  public static final String CLIENT_VERSION = "0.2.0";

  /**
   * The version of the Apify OpenAPI specification this client was generated and verified against.
   * Corresponds to the {@code info.version} field of the Apify OpenAPI document.
   */
  public static final String API_SPEC_VERSION = "v2-2026-07-10T105921Z";

  private Version() {}
}
