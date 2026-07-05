package com.apify.client;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for API resource models. It captures any JSON keys not mapped to a modelled field in
 * an {@link #getExtra() extra} map, so additive changes to the API never break deserialization
 * (forward compatibility).
 */
public abstract class ApifyResource {

  @JsonIgnore private final Map<String, Object> extra = new LinkedHashMap<>();

  @JsonAnySetter
  void putExtra(String key, Object value) {
    extra.put(key, value);
  }

  /** Any fields returned by the API that are not mapped to a typed property on this model. */
  @JsonAnyGetter
  public Map<String, Object> getExtra() {
    return Collections.unmodifiableMap(extra);
  }
}
