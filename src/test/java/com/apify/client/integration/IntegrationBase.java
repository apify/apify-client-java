package com.apify.client.integration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.apify.client.ApifyClient;
import java.security.SecureRandom;

/**
 * Shared setup for the integration test suite.
 *
 * <p>All integration tests require a valid {@code APIFY_TOKEN} for the test account. The API base
 * URL is taken from {@code APIFY_API_URL} (which includes the {@code /v2} suffix) and falls back to
 * {@code https://api.apify.com/v2}.
 *
 * <p>Tests are designed to run concurrently — including against the same test account from several
 * language clients at once — so every test creates uniquely-named resources and cleans them up.
 */
abstract class IntegrationBase {

  /** The integration-test contract fallback base URL. */
  static final String DEFAULT_API_URL = "https://api.apify.com/v2";

  private static final SecureRandom RANDOM = new SecureRandom();

  /**
   * Derives the client base URL from an optional {@code APIFY_API_URL}. The variable includes the
   * {@code /v2} suffix (per the integration-test contract) and falls back to {@link
   * #DEFAULT_API_URL}. Since the client appends {@code /v2} itself, the suffix is stripped.
   */
  static String resolveBaseUrl(String apiUrl) {
    if (apiUrl == null || apiUrl.isEmpty()) {
      apiUrl = DEFAULT_API_URL;
    }
    String trimmed = apiUrl;
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    if (trimmed.endsWith("/v2")) {
      trimmed = trimmed.substring(0, trimmed.length() - "/v2".length());
    }
    return trimmed;
  }

  /** Returns a configured client, or skips the test if {@code APIFY_TOKEN} is unset. */
  static ApifyClient requireClient() {
    String token = System.getenv("APIFY_TOKEN");
    assumeTrue(token != null && !token.isEmpty(), "skipping: APIFY_TOKEN is not set");
    return ApifyClient.builder()
        .token(token)
        .baseUrl(resolveBaseUrl(System.getenv("APIFY_API_URL")))
        .build();
  }

  /**
   * Generates a collision-resistant resource name for test isolation. The random component lets the
   * same test run in parallel (across processes and languages) without clobbering shared state.
   */
  static String uniqueName(String prefix) {
    byte[] buf = new byte[6];
    RANDOM.nextBytes(buf);
    StringBuilder hex = new StringBuilder();
    for (byte b : buf) {
      hex.append(String.format("%02x", b));
    }
    return "java-test-" + prefix + "-" + hex;
  }
}
