package com.apify.client.build;

import com.apify.client.ApifyResource;

/** Additional information about an Actor, from {@link ActorDefinition#getMeta()}. */
public final class ActorDefinitionMeta extends ApifyResource {
  private String templateId;
  private String generatedBy;

  /** The ID of the template from which the Actor was created. */
  public String getTemplateId() {
    return templateId;
  }

  /** The tool or model that generated the Actor. */
  public String getGeneratedBy() {
    return generatedBy;
  }
}
