package com.apify.client.run;

import com.apify.client.internal.ResourceContext;

/** Configures {@link RunClient#metamorph}. */
public final class MetamorphOptions {
  private String build;
  private String contentType;

  /** Optionally pins the target Actor's build (unset for default). */
  public MetamorphOptions build(String build) {
    this.build = build;
    return this;
  }

  /** The content type of the input body. Defaults to {@code application/json}. */
  public MetamorphOptions contentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  String buildValue() {
    return build;
  }

  String contentTypeOrDefault() {
    return (contentType != null && !contentType.isEmpty())
        ? contentType
        : ResourceContext.CONTENT_TYPE_JSON;
  }
}
