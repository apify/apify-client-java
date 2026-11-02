package com.apify.client.http;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.Encoder;
import java.io.IOException;

/**
 * An {@link HttpCompressor} that brotli-encodes request bodies ({@code Content-Encoding: br}).
 *
 * <p>It needs a platform-specific native library bundled with {@code brotli4j}; {@link
 * #isAvailable()} tells whether one loaded. The client uses this compressor by default where it is
 * available and gzip elsewhere.
 */
public final class BrotliHttpCompressor implements HttpCompressor {

  private static final int MAX_QUALITY = 11;

  private static final int DEFAULT_QUALITY = 6;

  private final int quality;

  /**
   * Whether the brotli native library loaded for the running platform. Brotli needs a native codec
   * that is not bundled for every OS and CPU; the check never throws.
   */
  public static boolean isAvailable() {
    try {
      Brotli4jLoader.ensureAvailability();
      return true;
    } catch (Throwable t) {
      // Catch Throwable: native loading raises UnsatisfiedLinkError/NoClassDefFoundError, not only
      // Exception, and "unavailable" is the answer in every such case.
      return false;
    }
  }

  /**
   * A compressor with the default quality, 6.
   *
   * @throws IllegalStateException if the brotli native library is not available on this platform
   */
  public BrotliHttpCompressor() {
    this(DEFAULT_QUALITY);
  }

  /**
   * A compressor with the given quality.
   *
   * @param quality the compression quality from 0 (fastest) to 11 (smallest output)
   * @throws IllegalArgumentException if {@code quality} is outside 0-11
   * @throws IllegalStateException if the brotli native library is not available on this platform
   */
  public BrotliHttpCompressor(int quality) {
    if (quality < 0 || quality > MAX_QUALITY) {
      throw new IllegalArgumentException("brotli quality must be between 0 and 11, was " + quality);
    }
    if (!isAvailable()) {
      throw new IllegalStateException(
          "the brotli native library is not available on this platform; use GzipHttpCompressor");
    }
    this.quality = quality;
  }

  @Override
  public String contentEncoding() {
    return "br";
  }

  @Override
  public byte[] compress(byte[] data) throws IOException {
    return Encoder.compress(data, new Encoder.Parameters().setQuality(quality));
  }
}
