package com.apify.client.actor;

import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;

/**
 * Configures {@link ActorClient#validateInput(Object, ValidateInputOptions)}. All fields are
 * optional.
 */
public final class ValidateInputOptions {
  private String build;
  private String contentType;

  /** The tag or number of the build whose input schema is used for validation. */
  public ValidateInputOptions build(String build) {
    this.build = build;
    return this;
  }

  /** The content type of the input body. Defaults to {@code application/json}. */
  public ValidateInputOptions contentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  String contentTypeOrDefault() {
    return (contentType != null && !contentType.isEmpty())
        ? contentType
        : ResourceContext.CONTENT_TYPE_JSON;
  }

  void apply(QueryParams q) {
    q.addString("build", build);
  }
}
