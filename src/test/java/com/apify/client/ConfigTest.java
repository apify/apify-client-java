package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Offline tests for the {@code APIFY_API_URL} → base-URL resolution used by the integration suite
 * (the client appends {@code /v2}, so the env-var suffix must be stripped).
 */
class ConfigTest {

  /** Mirror of the integration base's resolution logic (kept in sync with IntegrationBase). */
  private static String resolveBaseUrl(String apiUrl) {
    if (apiUrl == null || apiUrl.isEmpty()) {
      apiUrl = "https://api.apify.com/v2";
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

  @Test
  void resolveBaseUrlDefault() {
    assertEquals("https://api.apify.com", resolveBaseUrl(""));
    assertEquals("https://api.apify.com", resolveBaseUrl(null));
  }

  @Test
  void resolveBaseUrlStripsV2() {
    assertEquals("https://api.example.com", resolveBaseUrl("https://api.example.com/v2"));
    assertEquals("https://api.example.com", resolveBaseUrl("https://api.example.com/v2/"));
  }

  @Test
  void resolvedBaseUrlFeedsClientBaseUrl() {
    // The client re-appends /v2, reproducing the original APIFY_API_URL value.
    String base = resolveBaseUrl("https://api.example.com/v2");
    ApifyClient client = ApifyClient.builder().token("t").baseUrl(base).build();
    assertEquals("https://api.example.com/v2", client.getApiBaseUrl());
  }
}
