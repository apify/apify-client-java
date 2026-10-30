package com.apify.client.http;

import java.io.IOException;

/**
 * Compresses request bodies before they are sent. The client compresses a body only when it is at
 * least 1 KiB, has a compressible content type and carries no {@code Content-Encoding} header of
 * its own; the configured compressor then encodes it and its {@link #contentEncoding()} is sent as
 * the {@code Content-Encoding} header. Streams are never compressed.
 *
 * <p>Use {@link BrotliHttpCompressor} (the default, where its native library is available) or
 * {@link GzipHttpCompressor}, or implement this interface for another coding. Configure it with
 * {@link com.apify.client.ApifyClientBuilder#compression(HttpCompressor)}. Implementations must be
 * safe for concurrent use.
 */
public interface HttpCompressor {

  /** The {@code Content-Encoding} token that describes the output of {@link #compress}. */
  String contentEncoding();

  /**
   * Compresses {@code data}. A failure fails the request: the client does not fall back to another
   * coding.
   *
   * @throws IOException if the data cannot be compressed
   */
  byte[] compress(byte[] data) throws IOException;
}
