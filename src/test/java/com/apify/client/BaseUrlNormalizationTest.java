package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Offline tests for {@code ApifyClientBuilder}'s {@code baseUrl}/{@code publicBaseUrl}
 * normalization: trailing slashes are stripped and {@code /v2} is appended, unless the URL's path
 * already ends with it. Mirrors the reference JS client's table-driven regression test for the same
 * bug (a caller-supplied {@code .../v2} used to become {@code .../v2/v2}).
 */
class BaseUrlNormalizationTest {

  @ParameterizedTest
  @CsvSource({
    "https://example.com,                 https://example.com/v2",
    "https://example.com/,                https://example.com/v2",
    "https://example.com/v2,              https://example.com/v2",
    "https://example.com/v2/,             https://example.com/v2",
    "https://example.com/v2//,            https://example.com/v2",
    "https://example.com/proxy/v2,        https://example.com/proxy/v2",
    "https://example.com/apiv2,           https://example.com/apiv2/v2",
    // A bare "v2" host, not a "/v2" path: must still gain the version path, even though the
    // scheme's own "//" happens to precede a host spelled "v2" (a naive string-suffix check on the
    // whole URL would be fooled by this).
    "https://v2,                          https://v2/v2",
  })
  void normalizesBaseUrl(String input, String expected) {
    ApifyClient client = ApifyClient.builder().token("t").baseUrl(input).build();
    assertEquals(expected, client.getApiBaseUrl());
  }

  @ParameterizedTest
  @CsvSource({
    "https://example.com,     https://example.com/v2",
    "https://example.com/v2,  https://example.com/v2",
  })
  void normalizesPublicBaseUrlIndependently(String input, String expected) {
    ApifyClient client =
        ApifyClient.builder()
            .token("t")
            .baseUrl("https://api.apify.com")
            .publicBaseUrl(input)
            .build();
    assertEquals(expected, client.getPublicApiBaseUrl());
  }

  @Test
  void defaultBaseUrlIsVersioned() {
    ApifyClient client = ApifyClient.builder().token("t").build();
    assertEquals("https://api.apify.com/v2", client.getApiBaseUrl());
  }
}
