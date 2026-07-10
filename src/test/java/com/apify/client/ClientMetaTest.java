package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  void userAgentOsTokenIsShortAndLowercase() {
    // The OS token must be a short, lowercase platform identifier (linux/darwin/win32/…), matching
    // the reference JS client's os.platform() output — never the human-readable os.name.
    String token = ApifyClientBuilder.platformToken();
    assertFalse(token.isEmpty(), "platform token must not be empty");
    assertEquals(
        token.toLowerCase(java.util.Locale.ROOT), token, "token must be lowercase: " + token);
    assertFalse(token.contains(" "), "token must not contain spaces: " + token);

    String ua = ApifyClient.create("token").getUserAgent();
    assertTrue(ua.contains("(" + token + "; Java/"), ua);
  }

  @Test
  void platformTokenMapsEachOsToAlignedIdentifier() {
    // The aligned tokens must match Node's os.platform() output used by the reference JS client.
    assertEquals("linux", ApifyClientBuilder.platformToken("Linux", "OpenJDK 64-Bit Server VM"));
    // "Mac OS X" and a bare "Darwin" must both map to darwin. "Darwin" contains "win", so this
    // guards the ordering fix that keeps it from being misread as win32.
    assertEquals(
        "darwin", ApifyClientBuilder.platformToken("Mac OS X", "OpenJDK 64-Bit Server VM"));
    assertEquals("darwin", ApifyClientBuilder.platformToken("Darwin", "OpenJDK 64-Bit Server VM"));
    assertEquals("win32", ApifyClientBuilder.platformToken("Windows 10", "Java HotSpot(TM) VM"));
    // Android reports os.name == "Linux" but runs on the Dalvik VM.
    assertEquals("android", ApifyClientBuilder.platformToken("Linux", "Dalvik"));
    assertEquals("unknown", ApifyClientBuilder.platformToken("", ""));
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
