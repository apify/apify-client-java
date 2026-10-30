package com.apify.client.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/** An {@link HttpCompressor} that gzip-encodes request bodies ({@code Content-Encoding: gzip}). */
public final class GzipHttpCompressor implements HttpCompressor {

  /** The highest compression level, and the default. */
  private static final int MAX_LEVEL = 9;

  private static final int MIN_LEVEL = 1;

  private final int level;

  /** A compressor with the default level, 9. */
  public GzipHttpCompressor() {
    this(MAX_LEVEL);
  }

  /**
   * A compressor with the given level.
   *
   * @param level the compression level from 1 (fastest) to 9 (smallest output)
   * @throws IllegalArgumentException if {@code level} is outside 1-9
   */
  public GzipHttpCompressor(int level) {
    if (level < MIN_LEVEL || level > MAX_LEVEL) {
      throw new IllegalArgumentException("gzip level must be between 1 and 9, was " + level);
    }
    this.level = level;
  }

  @Override
  public String contentEncoding() {
    return "gzip";
  }

  @Override
  public byte[] compress(byte[] data) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
    try (GZIPOutputStream gzip =
        new GZIPOutputStream(out) {
          {
            def.setLevel(level);
          }
        }) {
      gzip.write(data);
    }
    return out.toByteArray();
  }
}
