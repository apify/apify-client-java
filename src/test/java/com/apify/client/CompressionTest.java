package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.decoder.Decoder;
import com.aayushatharva.brotli4j.decoder.DirectDecompress;
import com.apify.client.internal.HttpClientCore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

/**
 * Offline tests for request-body compression. Mirrors the reference JS client: bodies of at least
 * 1024 bytes are compressed and announced via {@code Content-Encoding}, smaller bodies are sent
 * verbatim. The client prefers brotli ({@code br}) and falls back to gzip; both codings are
 * exercised here — the brotli/gzip decision (a pure function) directly, and the live client path
 * via a real request.
 */
class CompressionTest {

  private static final String STORE_ID = "store-id";
  private static final String RECORD_KEY = "record";
  private static final String CONTENT_TYPE = "application/octet-stream";

  private static ApifyClient client(MockBackend backend) {
    return ApifyClient.builder().token("t").httpBackend(backend).maxRetries(0).build();
  }

  private static byte[] gunzip(byte[] data) throws IOException {
    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
      return in.readAllBytes();
    }
  }

  private static byte[] unbrotli(byte[] data) throws IOException {
    Brotli4jLoader.ensureAvailability();
    DirectDecompress result = Decoder.decompress(data);
    return result.getDecompressedData();
  }

  private static byte[] payload(int size, byte fill) {
    byte[] p = new byte[size];
    Arrays.fill(p, fill);
    return p;
  }

  /**
   * Whether a brotli native codec is expected to load here. The native codec is test-scoped only
   * (see {@code pom.xml}: {@code native-linux-x86_64}, {@code test} scope) — the published artifact
   * deliberately ships no native by default, so a consumer gets gzip unless they opt in by adding
   * their platform's brotli4j native themselves. This test-only native targets glibc linux x86_64
   * (this project's CI and typical containers); on musl (Alpine), 32-bit, other CPU arches, or when
   * the test native is absent, the client falls back to gzip. Tests that force the brotli codec are
   * gated on this so they run where brotli is expected and skip (not error) where gzip applies.
   */
  private static boolean nativeBrotliExpected() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    boolean linux = os.contains("nux");
    // Only x86_64/amd64: the test-scoped native dependency is native-linux-x86_64 only (see the
    // class javadoc above), so aarch64/arm64 hosts have no test native and must not expect brotli.
    boolean commonArch = arch.equals("amd64") || arch.equals("x86_64");
    boolean musl =
        Files.exists(Path.of("/lib/ld-musl-x86_64.so.1"))
            || Files.exists(Path.of("/lib/ld-musl-aarch64.so.1"));
    return linux && commonArch && !musl;
  }

  // --- Pure encoding decision: both paths are directly reachable and verified round-trip. ---

  @Test
  void brotliPathEncodesAsBrAndRoundTrips() throws IOException {
    assumeTrue(HttpClientCore.brotliAvailable(), "no brotli native codec on this platform");
    byte[] payload = payload(4096, (byte) 'a');
    HttpClientCore.Compressed c = HttpClientCore.compress(payload, true);

    assertEquals("br", c.encoding, "brotli path must announce Content-Encoding: br");
    assertTrue(c.body.length < payload.length, "brotli-compressed body should be smaller");
    assertArrayEquals(
        payload, unbrotli(c.body), "server must recover the original body from brotli");
  }

  @Test
  void gzipPathEncodesAsGzipAndRoundTrips() throws IOException {
    byte[] payload = payload(4096, (byte) 'a');
    HttpClientCore.Compressed c = HttpClientCore.compress(payload, false);

    assertEquals("gzip", c.encoding, "gzip fallback must announce Content-Encoding: gzip");
    assertTrue(c.body.length < payload.length, "gzip-compressed body should be smaller");
    assertArrayEquals(payload, gunzip(c.body), "server must recover the original body from gzip");
  }

  // --- Brotli native codec must load where the test-scoped native applies, so brotli runs. ---

  @Test
  void brotliNativeCodecLoadsWhereTestNativeApplies() {
    assumeTrue(
        nativeBrotliExpected(), "no test-scoped brotli native for this platform; gzip fallback");
    assertTrue(
        HttpClientCore.brotliAvailable(),
        "brotli native codec must load on glibc linux x86_64 (this project's test-scoped native) so"
            + " the preferred brotli path runs");
  }

  // --- Live client path: uses the preferred coding and round-trips through the backend. ---

  @Test
  void largeBodyIsCompressedWithPreferredEncoding() throws IOException {
    MockBackend backend = MockBackend.ofConstant(201, "");
    byte[] payload = payload(4096, (byte) 'a');

    client(backend).keyValueStore(STORE_ID).setRecord(RECORD_KEY, payload, CONTENT_TYPE);

    boolean brotli = HttpClientCore.brotliAvailable();
    String expected = brotli ? "br" : "gzip";
    assertEquals(
        expected,
        backend.lastHeaders.firstValue("Content-Encoding").orElse(null),
        "large body must be sent with the platform's preferred Content-Encoding");
    assertTrue(backend.lastBodyBytes.length < payload.length, "compressed body should be smaller");
    byte[] recovered = brotli ? unbrotli(backend.lastBodyBytes) : gunzip(backend.lastBodyBytes);
    assertArrayEquals(payload, recovered, "server must be able to recover the original body");
  }

  @Test
  void smallBodyIsNotCompressed() {
    MockBackend backend = MockBackend.ofConstant(201, "");
    byte[] payload = payload(16, (byte) 'b');

    client(backend).keyValueStore(STORE_ID).setRecord(RECORD_KEY, payload, CONTENT_TYPE);

    assertFalse(
        backend.lastHeaders.firstValue("Content-Encoding").isPresent(),
        "sub-threshold body must not carry a Content-Encoding header");
    assertArrayEquals(payload, backend.lastBodyBytes, "small body must be sent verbatim");
  }

  @Test
  void bodyExactlyAtThresholdIsCompressed() throws IOException {
    MockBackend backend = MockBackend.ofConstant(201, "");
    byte[] payload = payload(1024, (byte) 'c');

    client(backend).keyValueStore(STORE_ID).setRecord(RECORD_KEY, payload, CONTENT_TYPE);

    boolean brotli = HttpClientCore.brotliAvailable();
    assertEquals(
        brotli ? "br" : "gzip",
        backend.lastHeaders.firstValue("Content-Encoding").orElse(null),
        "a body at exactly the 1024-byte threshold must be compressed");
    byte[] recovered = brotli ? unbrotli(backend.lastBodyBytes) : gunzip(backend.lastBodyBytes);
    assertArrayEquals(payload, recovered);
  }
}
