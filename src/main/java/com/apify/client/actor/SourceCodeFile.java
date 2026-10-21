package com.apify.client.actor;

import com.apify.client.ApifyResource;

/** A single file in an Actor's source code. */
public final class SourceCodeFile extends ApifyResource {
  private String name;
  private String format;
  private String content;

  /** The path of the file relative to the Actor's root directory. */
  public String getName() {
    return name;
  }

  /**
   * The format of {@link #getContent()}: {@code "TEXT"} for plain text, {@code "BASE64"} for
   * encoded content.
   */
  public String getFormat() {
    return format;
  }

  /** The contents of the file, interpreted according to {@link #getFormat()}. */
  public String getContent() {
    return content;
  }
}
