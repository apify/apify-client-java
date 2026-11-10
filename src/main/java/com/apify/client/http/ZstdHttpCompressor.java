package com.apify.client.http;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.util.Native;

/**
 * An {@link HttpCompressor} that zstd-encodes request bodies ({@code Content-Encoding: zstd}).
 *
 * <p>It needs the optional {@code com.github.luben:zstd-jni} dependency; {@link #isAvailable()}
 * tells whether it loaded.
 */
public final class ZstdHttpCompressor implements HttpCompressor {

  private static final int MIN_LEVEL = 1;

  /**
   * zstd goes up to 22, but levels 20 to 22 use a window above the 8 MB that RFC 9659 requires HTTP
   * decoders to support, so the output could fail to decode in a browser.
   */
  private static final int MAX_LEVEL = 19;

  /** zstd's own default: fast, with a ratio close to gzip at its best level. */
  public static final int DEFAULT_LEVEL = 3;

  private final int level;

  /** Whether the zstd-jni library and its native codec loaded. Never throws. */
  public static boolean isAvailable() {
    try {
      Native.load();
      return true;
    } catch (Throwable t) {
      // Catch Throwable: a missing class or native library raises LinkageError, not Exception.
      return false;
    }
  }

  /**
   * A compressor with the default level, {@value #DEFAULT_LEVEL}.
   *
   * @throws IllegalStateException if zstd-jni is not available
   */
  public ZstdHttpCompressor() {
    this(DEFAULT_LEVEL);
  }

  /**
   * A compressor with the given level.
   *
   * @param level the compression level from 1 (fastest) to 19 (smallest output)
   * @throws IllegalArgumentException if {@code level} is outside 1-19
   * @throws IllegalStateException if zstd-jni is not available
   */
  public ZstdHttpCompressor(int level) {
    if (level < MIN_LEVEL || level > MAX_LEVEL) {
      throw new IllegalArgumentException("zstd level must be between 1 and 19, was " + level);
    }
    if (!isAvailable()) {
      throw new IllegalStateException(
          "zstd-jni is not on the classpath or has no native library for this platform");
    }
    this.level = level;
  }

  @Override
  public String contentEncoding() {
    return "zstd";
  }

  @Override
  public byte[] compress(byte[] data) {
    return Zstd.compress(data, level);
  }
}
