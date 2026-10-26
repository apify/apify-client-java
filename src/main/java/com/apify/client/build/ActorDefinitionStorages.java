package com.apify.client.build;

import com.apify.client.ApifyResource;
import java.util.Collections;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** The storage schemas of an Actor, from {@link ActorDefinition#getStorages()}. */
public final class ActorDefinitionStorages extends ApifyResource {
  private JsonNode dataset;
  private Map<String, JsonNode> datasets;
  private JsonNode keyValueStore;

  /**
   * The schema of the items in the Actor's dataset, as defined in the Actor's {@code
   * .actor/actor.json}.
   */
  public JsonNode getDataset() {
    return dataset;
  }

  /**
   * Maps dataset aliases to the schemas of the Actor's datasets; an empty map when the Actor
   * defines none.
   */
  public Map<String, JsonNode> getDatasets() {
    return datasets == null ? Map.of() : Collections.unmodifiableMap(datasets);
  }

  /** The schema of the Actor's default key-value store. */
  public JsonNode getKeyValueStore() {
    return keyValueStore;
  }
}
