package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

/**
 * Offline tests for request-body gzip compression. Mirrors the reference JS client's behaviour:
 * bodies of at least 1024 bytes are compressed and announced via {@code Content-Encoding}, smaller
 * bodies are sent verbatim.
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

  @Test
  void largeBodyIsGzipCompressed() throws IOException {
    MockBackend backend = MockBackend.ofConstant(201, "");
    byte[] payload = new byte[4096];
    Arrays.fill(payload, (byte) 'a');

    client(backend).keyValueStore(STORE_ID).setRecord(RECORD_KEY, payload, CONTENT_TYPE);

    assertEquals(
        "gzip",
        backend.lastHeaders.firstValue("Content-Encoding").orElse(null),
        "large body must be sent with Content-Encoding: gzip");
    assertTrue(backend.lastBodyBytes.length < payload.length, "compressed body should be smaller");
    assertArrayEquals(
        payload, gunzip(backend.lastBodyBytes), "server must be able to recover the original body");
  }

  @Test
  void smallBodyIsNotCompressed() {
    MockBackend backend = MockBackend.ofConstant(201, "");
    byte[] payload = new byte[16];
    Arrays.fill(payload, (byte) 'b');

    client(backend).keyValueStore(STORE_ID).setRecord(RECORD_KEY, payload, CONTENT_TYPE);

    assertFalse(
        backend.lastHeaders.firstValue("Content-Encoding").isPresent(),
        "sub-threshold body must not carry a Content-Encoding header");
    assertArrayEquals(payload, backend.lastBodyBytes, "small body must be sent verbatim");
  }

  @Test
  void bodyExactlyAtThresholdIsCompressed() throws IOException {
    MockBackend backend = MockBackend.ofConstant(201, "");
    byte[] payload = new byte[1024];
    Arrays.fill(payload, (byte) 'c');

    client(backend).keyValueStore(STORE_ID).setRecord(RECORD_KEY, payload, CONTENT_TYPE);

    assertEquals(
        "gzip",
        backend.lastHeaders.firstValue("Content-Encoding").orElse(null),
        "a body at exactly the 1024-byte threshold must be compressed");
    assertArrayEquals(payload, gunzip(backend.lastBodyBytes));
  }
}
