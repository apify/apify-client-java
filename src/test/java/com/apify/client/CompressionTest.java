package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.decoder.Decoder;
import com.aayushatharva.brotli4j.decoder.DirectDecompress;
import com.apify.client.http.BrotliHttpCompressor;
import com.apify.client.http.GzipHttpCompressor;
import com.apify.client.http.HttpCompressor;
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
 * verbatim. The client uses brotli ({@code br}) by default where its native codec is available and
 * gzip elsewhere; the compressor is configurable, and its failure fails the request. Both
 * compressors are exercised directly, and the live client path via a real request.
 */
class CompressionTest {

  private static final String STORE_ID = "store-id";
  private static final String RECORD_KEY = "record";
  private static final String CONTENT_TYPE = "application/octet-stream";

  private static ApifyClient client(MockTransport backend) {
    return ApifyClient.builder().token("t").httpTransport(backend).maxRetries(0).build();
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
   * Whether a brotli native codec is expected to load here. brotli4j bundles glibc x86_64/aarch64
   * binaries (what CI's ubuntu-latest and typical server runtimes use); on musl (Alpine), 32-bit,
   * or other CPU arches none ships and the client falls back to gzip. Tests that force the brotli
   * codec are gated on this so they run where brotli is expected and skip (not error) where gzip
   * applies.
   */
  private static boolean nativeBrotliExpected() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    boolean linux = os.contains("nux");
    boolean commonArch =
        arch.equals("amd64")
            || arch.equals("x86_64")
            || arch.equals("aarch64")
            || arch.equals("arm64");
    boolean musl =
        Files.exists(Path.of("/lib/ld-musl-x86_64.so.1"))
            || Files.exists(Path.of("/lib/ld-musl-aarch64.so.1"));
    return linux && commonArch && !musl;
  }

  // --- The compressors themselves: both codings are reachable and verified round-trip. ---

  @Test
  void brotliCompressorEncodesAsBrAndRoundTrips() throws IOException {
    assumeTrue(BrotliHttpCompressor.isAvailable(), "no brotli native codec on this platform");
    byte[] payload = payload(4096, (byte) 'a');
    HttpCompressor compressor = new BrotliHttpCompressor();
    byte[] compressed = compressor.compress(payload);

    assertEquals("br", compressor.contentEncoding());
    assertTrue(compressed.length < payload.length, "brotli-compressed body should be smaller");
    assertArrayEquals(payload, unbrotli(compressed));
  }

  @Test
  void gzipCompressorEncodesAsGzipAndRoundTrips() throws IOException {
    byte[] payload = payload(4096, (byte) 'a');
    HttpCompressor compressor = new GzipHttpCompressor();
    byte[] compressed = compressor.compress(payload);

    assertEquals("gzip", compressor.contentEncoding());
    assertTrue(compressed.length < payload.length, "gzip-compressed body should be smaller");
    assertArrayEquals(payload, gunzip(compressed));
  }

  @Test
  void gzipDefaultsToLevelSix() throws IOException {
    assertEquals(6, GzipHttpCompressor.DEFAULT_LEVEL);
    java.util.Random random = new java.util.Random(7);
    String[] words = new String[300];
    for (int i = 0; i < words.length; i++) {
      StringBuilder word = new StringBuilder();
      for (int j = 3 + random.nextInt(6); j > 0; j--) {
        word.append((char) ('a' + random.nextInt(26)));
      }
      words[i] = word.toString();
    }
    StringBuilder text = new StringBuilder();
    while (text.length() < 200_000) {
      text.append(words[random.nextInt(words.length)]).append(' ');
    }
    byte[] payload = text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

    byte[] byDefault = new GzipHttpCompressor().compress(payload);

    assertArrayEquals(new GzipHttpCompressor(6).compress(payload), byDefault);
    assertFalse(
        java.util.Arrays.equals(new GzipHttpCompressor(9).compress(payload), byDefault),
        "the payload must tell level 6 from level 9");
  }

  @Test
  void gzipLevelTradesSizeForSpeed() throws IOException {
    byte[] payload = new byte[64 * 1024];
    new java.util.Random(7).nextBytes(payload);
    for (int i = 0; i < payload.length; i += 3) {
      payload[i] = 'x'; // partly compressible, so the levels differ
    }
    byte[] fastest = new GzipHttpCompressor(1).compress(payload);
    byte[] smallest = new GzipHttpCompressor(9).compress(payload);

    assertArrayEquals(payload, gunzip(fastest));
    assertArrayEquals(payload, gunzip(smallest));
    assertTrue(smallest.length <= fastest.length, "level 9 must not be larger than level 1");
  }

  @Test
  void compressorsRejectAnOutOfRangeQualityAtConstruction() {
    assertThrows(IllegalArgumentException.class, () -> new GzipHttpCompressor(0));
    assertThrows(IllegalArgumentException.class, () -> new GzipHttpCompressor(10));
    assertThrows(IllegalArgumentException.class, () -> new BrotliHttpCompressor(-1));
    assertThrows(IllegalArgumentException.class, () -> new BrotliHttpCompressor(12));
  }

  // --- The configured compressor is the one the client runs, with no silent fallback. ---

  @Test
  void configuredGzipCompressorIsUsedForLargeBodies() throws IOException {
    MockTransport backend = MockTransport.ofConstant(201, "");
    byte[] payload = payload(4096, (byte) 'a');

    ApifyClient.builder()
        .token("t")
        .httpTransport(backend)
        .maxRetries(0)
        .compression(new GzipHttpCompressor(3))
        .build()
        .keyValueStore(STORE_ID)
        .setRecord(RECORD_KEY, payload, CONTENT_TYPE)
        .join();

    assertEquals("gzip", backend.lastHeaders.firstValue("Content-Encoding").orElse(null));
    assertArrayEquals(payload, gunzip(backend.lastBodyBytes));
  }

  @Test
  void customCompressorIsUsedAndItsFailureFailsTheRequest() {
    HttpCompressor failing =
        new HttpCompressor() {
          @Override
          public String contentEncoding() {
            return "x-custom";
          }

          @Override
          public byte[] compress(byte[] data) throws IOException {
            throw new IOException("boom");
          }
        };
    MockTransport backend = MockTransport.ofConstant(201, "");
    ApifyClient client =
        ApifyClient.builder()
            .token("t")
            .httpTransport(backend)
            .maxRetries(0)
            .compression(failing)
            .build();

    var future =
        client
            .keyValueStore(STORE_ID)
            .setRecord(RECORD_KEY, payload(4096, (byte) 'a'), CONTENT_TYPE);

    var error = assertThrows(java.util.concurrent.CompletionException.class, future::join);
    assertTrue(error.getCause() instanceof com.apify.client.http.ApifyTransportException);
    assertEquals(0, backend.calls, "a body that could not be compressed must not be sent");
  }

  @Test
  void aCompressorThatThrowsAnErrorFailsTheFutureInsteadOfThrowing() {
    HttpCompressor broken =
        new HttpCompressor() {
          @Override
          public String contentEncoding() {
            return "x-broken";
          }

          @Override
          public byte[] compress(byte[] data) {
            throw new UnsatisfiedLinkError("native codec missing");
          }
        };
    MockTransport backend = MockTransport.ofConstant(201, "");
    ApifyClient client =
        ApifyClient.builder().token("t").httpTransport(backend).compression(broken).build();

    var future =
        client
            .keyValueStore(STORE_ID)
            .setRecord(RECORD_KEY, payload(4096, (byte) 'a'), CONTENT_TYPE);

    var error = assertThrows(java.util.concurrent.CompletionException.class, future::join);
    assertTrue(error.getCause() instanceof com.apify.client.http.ApifyTransportException);
    assertEquals(0, backend.calls);
  }

  @Test
  void builderRejectsNullCompression() {
    assertThrows(IllegalArgumentException.class, () -> ApifyClient.builder().compression(null));
  }

  // --- Brotli native codec must load where a native is bundled (CI/runtime), so brotli runs. ---

  @Test
  void brotliNativeCodecLoadsWhereBundled() {
    assumeTrue(nativeBrotliExpected(), "no brotli native bundled for this platform; gzip fallback");
    assertTrue(
        BrotliHttpCompressor.isAvailable(),
        "brotli native codec must load on glibc x86_64/aarch64 so the preferred brotli path runs");
  }

  // --- Live client path: uses the preferred coding and round-trips through the backend. ---

  @Test
  void largeBodyIsCompressedWithPreferredEncoding() throws IOException {
    MockTransport backend = MockTransport.ofConstant(201, "");
    byte[] payload = payload(4096, (byte) 'a');

    client(backend).keyValueStore(STORE_ID).setRecord(RECORD_KEY, payload, CONTENT_TYPE).join();

    boolean brotli = BrotliHttpCompressor.isAvailable();
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
    MockTransport backend = MockTransport.ofConstant(201, "");
    byte[] payload = payload(16, (byte) 'b');

    client(backend).keyValueStore(STORE_ID).setRecord(RECORD_KEY, payload, CONTENT_TYPE).join();

    assertFalse(
        backend.lastHeaders.firstValue("Content-Encoding").isPresent(),
        "sub-threshold body must not carry a Content-Encoding header");
    assertArrayEquals(payload, backend.lastBodyBytes, "small body must be sent verbatim");
  }

  // --- Content-type classification: skip compressing payloads that are already compressed. ---

  @Test
  void isCompressibleContentTypeClassifiesKnownMediaTypes() {
    // Missing/empty/text content types are compressible.
    assertTrue(HttpClientCore.isCompressibleContentType(null));
    assertTrue(HttpClientCore.isCompressibleContentType(""));
    assertTrue(HttpClientCore.isCompressibleContentType("application/json"));
    assertTrue(HttpClientCore.isCompressibleContentType("text/plain; charset=utf-8"));
    assertTrue(HttpClientCore.isCompressibleContentType("application/octet-stream"));
    // A structured-syntax suffix overrides an already-compressed prefix.
    assertTrue(HttpClientCore.isCompressibleContentType("image/svg+xml"));
    assertTrue(HttpClientCore.isCompressibleContentType("IMAGE/SVG+XML; charset=utf-8"));
    // A raw format under an already-compressed prefix is still compressible.
    assertTrue(HttpClientCore.isCompressibleContentType("image/bmp"));
    assertTrue(HttpClientCore.isCompressibleContentType("audio/wav"));

    // Media/archive prefixes and exact types already carry their own compression.
    assertFalse(HttpClientCore.isCompressibleContentType("image/png"));
    assertFalse(HttpClientCore.isCompressibleContentType("video/mp4"));
    assertFalse(HttpClientCore.isCompressibleContentType("audio/mpeg"));
    assertFalse(HttpClientCore.isCompressibleContentType("application/zip"));
    assertFalse(HttpClientCore.isCompressibleContentType("application/x-gzip"));
    assertFalse(HttpClientCore.isCompressibleContentType("font/woff2"));
    assertFalse(
        HttpClientCore.isCompressibleContentType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    // Case-insensitive and whitespace-tolerant.
    assertFalse(HttpClientCore.isCompressibleContentType("  Image/PNG  "));
  }

  @Test
  void alreadyCompressedContentTypeSkipsCompression() {
    MockTransport backend = MockTransport.ofConstant(201, "");
    byte[] payload = payload(4096, (byte) 'a'); // trivially compressible, well above the threshold

    client(backend).keyValueStore(STORE_ID).setRecord(RECORD_KEY, payload, "image/png").join();

    assertFalse(
        backend.lastHeaders.firstValue("Content-Encoding").isPresent(),
        "an already-compressed media type must not be run through the compressor");
    assertArrayEquals(payload, backend.lastBodyBytes, "body must be sent verbatim, uncompressed");
  }

  @Test
  void rawFormatUnderCompressedPrefixIsStillCompressed() throws IOException {
    MockTransport backend = MockTransport.ofConstant(201, "");
    byte[] payload = payload(4096, (byte) 'a');

    // image/bmp sits under the "image/" prefix but is raw, so it should still be compressed.
    client(backend).keyValueStore(STORE_ID).setRecord(RECORD_KEY, payload, "image/bmp").join();

    boolean brotli = BrotliHttpCompressor.isAvailable();
    assertEquals(
        brotli ? "br" : "gzip", backend.lastHeaders.firstValue("Content-Encoding").orElse(null));
    byte[] recovered = brotli ? unbrotli(backend.lastBodyBytes) : gunzip(backend.lastBodyBytes);
    assertArrayEquals(payload, recovered);
  }

  @Test
  void bodyExactlyAtThresholdIsCompressed() throws IOException {
    MockTransport backend = MockTransport.ofConstant(201, "");
    byte[] payload = payload(1024, (byte) 'c');

    client(backend).keyValueStore(STORE_ID).setRecord(RECORD_KEY, payload, CONTENT_TYPE).join();

    boolean brotli = BrotliHttpCompressor.isAvailable();
    assertEquals(
        brotli ? "br" : "gzip",
        backend.lastHeaders.firstValue("Content-Encoding").orElse(null),
        "a body at exactly the 1024-byte threshold must be compressed");
    byte[] recovered = brotli ? unbrotli(backend.lastBodyBytes) : gunzip(backend.lastBodyBytes);
    assertArrayEquals(payload, recovered);
  }
}
