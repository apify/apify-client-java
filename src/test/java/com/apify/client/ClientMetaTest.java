package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Offline tests for version constants, User-Agent formatting, and base-URL resolution. */
class ClientMetaTest {

  @Test
  void versionConstants() {
    assertTrue(Character.isDigit(Version.CLIENT_VERSION.charAt(0)), Version.CLIENT_VERSION);
    assertTrue(Version.API_SPEC_VERSION.startsWith("v2-"), Version.API_SPEC_VERSION);
    assertTrue(Version.API_SPEC_VERSION.endsWith("Z"), Version.API_SPEC_VERSION);
  }

  @Test
  void userAgentFormat() {
    String ua = ApifyClient.create("token").getUserAgent();
    assertTrue(ua.startsWith("ApifyClient/" + Version.CLIENT_VERSION + " ("), ua);
    assertTrue(ua.contains("Java/"), ua);
    assertTrue(ua.contains("isAtHome/"), ua);
    String after = ua.split("Java/", 2)[1];
    assertTrue(Character.isDigit(after.charAt(0)), "Java version must be a real version: " + ua);
  }

  @Test
  void userAgentIsAtHomeFlag() {
    ApifyClient off = ApifyClient.builder().token("t").isAtHomeFn(() -> false).build();
    assertTrue(off.getUserAgent().contains("isAtHome/false"), off.getUserAgent());
    ApifyClient on = ApifyClient.builder().token("t").isAtHomeFn(() -> true).build();
    assertTrue(on.getUserAgent().contains("isAtHome/true"), on.getUserAgent());
  }

  @Test
  void userAgentSuffix() {
    ApifyClient client = ApifyClient.builder().token("t").userAgentSuffix("MyTool/1.0").build();
    assertTrue(client.getUserAgent().endsWith("; MyTool/1.0"), client.getUserAgent());
  }

  @Test
  void baseUrlDefaultAndV2Suffix() {
    assertEquals("https://api.apify.com/v2", ApifyClient.create("t").getApiBaseUrl());
  }

  @Test
  void baseUrlOverrideAppendsV2() {
    ApifyClient client =
        ApifyClient.builder().token("t").baseUrl("https://api.example.com/").build();
    assertEquals("https://api.example.com/v2", client.getApiBaseUrl());
  }
}
