package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
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
  void clientVersionMatchesBuildVersion() throws IOException {
    // The Maven <version> in pom.xml and Version.CLIENT_VERSION must be bumped in lockstep on every
    // release. Nothing else fails the build if they diverge, and version-bump PRs are exactly where
    // that slips through, so this test compares Version.CLIENT_VERSION against the built
    // project.version (substituted into a filtered resource during the build). Hermetic: reads a
    // classpath resource, no network.
    Properties props = new Properties();
    try (InputStream in =
        ClientMetaTest.class.getResourceAsStream("/apify-client-build.properties")) {
      assertNotNull(
          in,
          "apify-client-build.properties missing from the test classpath; check the pom's filtered"
              + " testResource");
      props.load(in);
    }
    String buildVersion = props.getProperty("project.version");
    assertNotNull(buildVersion, "project.version not present in apify-client-build.properties");
    assertFalse(
        buildVersion.contains("${"),
        "project.version was not filtered (still a Maven placeholder): " + buildVersion);
    assertEquals(
        buildVersion,
        Version.CLIENT_VERSION,
        "pom.xml <version> and Version.CLIENT_VERSION must match; bump them together");
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
    assertEquals(token.toLowerCase(Locale.ROOT), token, "token must be lowercase: " + token);
    assertFalse(token.contains(" "), "token must not contain spaces: " + token);

    String ua = ApifyClient.create("token").getUserAgent();
    assertTrue(ua.contains("(" + token + "; Java/"), ua);
  }

  @Test
  void platformTokenMapsEachOsToAlignedIdentifier() {
    // Every token must exactly match Node's os.platform() output, so all Apify clients agree.
    assertEquals("linux", ApifyClientBuilder.platformToken("Linux", "OpenJDK 64-Bit Server VM"));
    // "Mac OS X" and a bare "Darwin" must both map to darwin. "Darwin" contains "win", so this
    // guards the ordering fix that keeps it from being misread as win32.
    assertEquals(
        "darwin", ApifyClientBuilder.platformToken("Mac OS X", "OpenJDK 64-Bit Server VM"));
    assertEquals("darwin", ApifyClientBuilder.platformToken("Darwin", "OpenJDK 64-Bit Server VM"));
    assertEquals("win32", ApifyClientBuilder.platformToken("Windows 10", "Java HotSpot(TM) VM"));
    assertEquals(
        "win32", ApifyClientBuilder.platformToken("Windows Server 2022", "Java HotSpot(TM) VM"));
    // Android reports os.name == "Linux" but runs on the Dalvik VM.
    assertEquals("android", ApifyClientBuilder.platformToken("Linux", "Dalvik"));
    // The Unix platforms Node's os.platform() can emit must map to the identical token.
    assertEquals("sunos", ApifyClientBuilder.platformToken("SunOS", "OpenJDK 64-Bit Server VM"));
    assertEquals("sunos", ApifyClientBuilder.platformToken("Solaris", "OpenJDK 64-Bit Server VM"));
    assertEquals(
        "freebsd", ApifyClientBuilder.platformToken("FreeBSD", "OpenJDK 64-Bit Server VM"));
    assertEquals(
        "openbsd", ApifyClientBuilder.platformToken("OpenBSD", "OpenJDK 64-Bit Server VM"));
    assertEquals("aix", ApifyClientBuilder.platformToken("AIX", "OpenJDK 64-Bit Server VM"));
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
