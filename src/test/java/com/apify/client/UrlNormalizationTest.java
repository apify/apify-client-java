package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.apify.client.dataset.Dataset;
import com.apify.client.internal.Json;
import com.apify.client.internal.UrlNormalizer;
import com.apify.client.run.ActorRun;
import com.apify.client.webhook.Webhook;
import org.junit.jupiter.api.Test;

/**
 * The spec's {@code format: uri} fields come back in the WHATWG URL serialization, so every Apify
 * client returns the same string for the same field.
 */
class UrlNormalizationTest {

  @Test
  void emptyPathBecomesASlashAndTheHostIsLowercased() {
    assertEquals("https://example.com/", UrlNormalizer.normalize("https://EXAMPLE.com"));
    assertEquals("http://example.com/a", UrlNormalizer.normalize("HTTP://Example.COM/a"));
  }

  @Test
  void defaultPortsAreDroppedAndOthersKept() {
    assertEquals("https://example.com/", UrlNormalizer.normalize("https://example.com:443"));
    assertEquals("http://example.com/", UrlNormalizer.normalize("http://example.com:80/"));
    assertEquals(
        "https://example.com:8443/x", UrlNormalizer.normalize("https://example.com:8443/x"));
    assertEquals("http://example.com:443/", UrlNormalizer.normalize("http://example.com:443/"));
  }

  @Test
  void nonAsciiHostsArePunycoded() {
    assertEquals(
        "https://xn--bcher-kva.example/", UrlNormalizer.normalize("https://bücher.example"));
  }

  @Test
  void unsafeCharactersArePercentEncodedAndExistingEscapesKept() {
    assertEquals(
        "https://example.com/a%20b?q=%22x%22#f",
        UrlNormalizer.normalize("https://example.com/a b?q=\"x\"#f"));
    assertEquals("https://example.com/%C3%A9", UrlNormalizer.normalize("https://example.com/é"));
    assertEquals("https://example.com/a%2Fb", UrlNormalizer.normalize("https://example.com/a%2Fb"));
  }

  @Test
  void dotSegmentsAreResolved() {
    assertEquals("https://example.com/b", UrlNormalizer.normalize("https://example.com/a/../b"));
    assertEquals("https://example.com/a/", UrlNormalizer.normalize("https://example.com/a/./"));
    assertEquals("https://example.com/", UrlNormalizer.normalize("https://example.com/.."));
  }

  @Test
  void queryAndFragmentAreKept() {
    assertEquals(
        "https://example.com/?a=1&b=2#top",
        UrlNormalizer.normalize("https://example.com?a=1&b=2#top"));
  }

  @Test
  void aNormalizedUrlStaysTheSame() {
    String url = "https://console.apify.com/actors/runs/abc?tab=output";
    assertEquals(url, UrlNormalizer.normalize(url));
  }

  @Test
  void userInfoKeepsItsSeparatorAndEncodesEachPart() {
    assertEquals(
        "https://user:pass@example.com/hook",
        UrlNormalizer.normalize("https://user:pass@example.com/hook"));
    assertEquals(
        "https://us%20er:p%40ss@example.com/",
        UrlNormalizer.normalize("https://us er:p@ss@Example.com"));
    assertEquals(
        "https://token@example.com/", UrlNormalizer.normalize("https://token@example.com"));
  }

  @Test
  void anInvalidPortLeavesTheValueAlone() {
    assertEquals(
        "https://example.com:99999999999/",
        UrlNormalizer.normalize("https://example.com:99999999999/"));
    assertEquals(
        "https://example.com:65536/", UrlNormalizer.normalize("https://example.com:65536/"));
    assertEquals(
        "https://example.com:65535/", UrlNormalizer.normalize("https://example.com:65535"));
    assertEquals("https://example.com:abc/", UrlNormalizer.normalize("https://example.com:abc/"));
  }

  @Test
  void valuesThatAreNotAbsoluteUrlsAreLeftAlone() {
    assertNull(UrlNormalizer.normalize(null));
    assertEquals("not a url", UrlNormalizer.normalize("not a url"));
    assertEquals("/relative/path", UrlNormalizer.normalize("/relative/path"));
    assertEquals("https:///nohost", UrlNormalizer.normalize("https:///nohost"));
  }

  @Test
  void responseModelsNormalizeTheirUrlFields() {
    String run =
        "{\"id\":\"r1\",\"consoleUrl\":\"https://CONSOLE.apify.com\","
            + "\"containerUrl\":\"https://abc.runs.apify.net:443\","
            + "\"output\":{\"dataset\":\"https://api.apify.com:443/v2/datasets/d\"}}";
    ActorRun parsed =
        Json.parse(run.getBytes(java.nio.charset.StandardCharsets.UTF_8), ActorRun.class);
    assertEquals("https://console.apify.com/", parsed.getConsoleUrl());
    assertEquals("https://abc.runs.apify.net/", parsed.getContainerUrl());
    assertEquals("https://api.apify.com/v2/datasets/d", parsed.getOutput().get("dataset"));

    Dataset dataset =
        Json.parse(
            "{\"id\":\"d\",\"itemsPublicUrl\":\"https://api.apify.com\",\"title\":\"T\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
            Dataset.class);
    assertEquals("https://api.apify.com/", dataset.getItemsPublicUrl());
    assertEquals("T", dataset.getTitle());

    Webhook webhook =
        Json.parse(
            "{\"id\":\"w\",\"requestUrl\":\"https://Example.com/hook\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
            Webhook.class);
    assertEquals("https://example.com/hook", webhook.getRequestUrl());
  }

  @Test
  void aNullUrlFieldStaysNull() {
    ActorRun parsed =
        Json.parse(
            "{\"id\":\"r1\",\"containerUrl\":null}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
            ActorRun.class);
    assertNull(parsed.getContainerUrl());
  }
}
