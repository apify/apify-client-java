package com.apify.client.internal;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Deserializes a {@code format: uri} string field of an API response into its {@linkplain
 * UrlNormalizer#normalize normalized} form. Internal to the client; models reference it with
 * {@code @JsonDeserialize}.
 */
public final class NormalizedUrlDeserializer extends StdDeserializer<String> {

  public NormalizedUrlDeserializer() {
    super(String.class);
  }

  @Override
  public String deserialize(JsonParser p, DeserializationContext ctxt) {
    return UrlNormalizer.normalize(p.getValueAsString());
  }
}
