package com.apify.client.build;

import com.apify.client.ApifyResource;
import java.util.Collections;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * The definition of an Actor, as stored in its {@code .actor/actor.json}, from {@link
 * Build#getActorDefinition()}. Every field is optional.
 */
public final class ActorDefinition extends ApifyResource {
  private Integer actorSpecification;
  private String name;
  private String title;
  private String description;
  private String version;
  private String buildTag;
  private ActorDefinitionMeta meta;
  private Map<String, String> environmentVariables;
  private String dockerfile;
  private String dockerContextDir;
  private String readme;
  private JsonNode input;
  private JsonNode output;
  private String changelog;
  private ActorDefinitionStorages storages;
  private JsonNode defaultMemoryMbytes;
  private Long minMemoryMbytes;
  private Long maxMemoryMbytes;
  private Boolean usesStandbyMode;
  private JsonNode webServerSchema;
  private String webServerMcpPath;

  /** The Actor specification version that this Actor follows; always {@code 1}. */
  public Integer getActorSpecification() {
    return actorSpecification;
  }

  /** The name of the Actor. */
  public String getName() {
    return name;
  }

  /** The human-readable display title of the Actor, shown in Apify Console and Apify Store. */
  public String getTitle() {
    return title;
  }

  /** The description of the Actor. */
  public String getDescription() {
    return description;
  }

  /** The version of the Actor, a dot-separated sequence of numbers (e.g. {@code "0.1"}). */
  public String getVersion() {
    return version;
  }

  /** The tag applied to a successful build of the Actor; {@code latest} when not specified. */
  public String getBuildTag() {
    return buildTag;
  }

  /** Additional information about the Actor. */
  public ActorDefinitionMeta getMeta() {
    return meta;
  }

  /**
   * Environment variables used during local development and deployment; an empty map when none are
   * defined.
   */
  public Map<String, String> getEnvironmentVariables() {
    return environmentVariables == null
        ? Map.of()
        : Collections.unmodifiableMap(environmentVariables);
  }

  /** The path to the Dockerfile used for building the Actor on the platform. */
  public String getDockerfile() {
    return dockerfile;
  }

  /** The path to the directory used as the Docker context when building the Actor. */
  public String getDockerContextDir() {
    return dockerContextDir;
  }

  /** The path to the README file for the Actor; {@code null} if there is none. */
  public String getReadme() {
    return readme;
  }

  /** The input schema object; {@code null} if the Actor has no input schema. */
  public JsonNode getInput() {
    return input;
  }

  /** The output schema object; {@code null} if the Actor has no output schema. */
  public JsonNode getOutput() {
    return output;
  }

  /** The path to the CHANGELOG file shown in the Actor's information tab; may be {@code null}. */
  public String getChangelog() {
    return changelog;
  }

  /** The schemas of the Actor's storages. */
  public ActorDefinitionStorages getStorages() {
    return storages;
  }

  /**
   * The default memory in megabytes used when the Actor is started: either a JSON integer or a
   * string with a dynamic memory expression (e.g. {@code "get(input, 'startUrls.length', 1) *
   * 1024"}).
   */
  public JsonNode getDefaultMemoryMbytes() {
    return defaultMemoryMbytes;
  }

  /** The minimum memory in megabytes required by the Actor. */
  public Long getMinMemoryMbytes() {
    return minMemoryMbytes;
  }

  /** The maximum memory in megabytes required by the Actor. */
  public Long getMaxMemoryMbytes() {
    return maxMemoryMbytes;
  }

  /** Whether Standby mode is enabled for the Actor. */
  public Boolean getUsesStandbyMode() {
    return usesStandbyMode;
  }

  /** The OpenAPI v3 specification of the web server running in the Actor. */
  public JsonNode getWebServerSchema() {
    return webServerSchema;
  }

  /** The HTTP path where the Actor's web server exposes its MCP server (e.g. {@code "/mcp"}). */
  public String getWebServerMcpPath() {
    return webServerMcpPath;
  }
}
