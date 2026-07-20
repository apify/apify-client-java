package com.apify.client.run;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resource-usage metrics for an {@link ActorRun}, broken down by billable unit. All fields are the
 * total consumption during the run's lifetime and {@code null} when not applicable to the run.
 *
 * <p>The API reports these keys in {@code SCREAMING_SNAKE_CASE} (e.g. {@code
 * "ACTOR_COMPUTE_UNITS"}); each field is mapped to its idiomatic camelCase Java name via
 * {@code @JsonProperty}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ActorRunUsage {
  @JsonProperty("ACTOR_COMPUTE_UNITS")
  private Double actorComputeUnits;

  @JsonProperty("DATASET_READS")
  private Double datasetReads;

  @JsonProperty("DATASET_WRITES")
  private Double datasetWrites;

  @JsonProperty("KEY_VALUE_STORE_READS")
  private Double keyValueStoreReads;

  @JsonProperty("KEY_VALUE_STORE_WRITES")
  private Double keyValueStoreWrites;

  @JsonProperty("KEY_VALUE_STORE_LISTS")
  private Double keyValueStoreLists;

  @JsonProperty("REQUEST_QUEUE_READS")
  private Double requestQueueReads;

  @JsonProperty("REQUEST_QUEUE_WRITES")
  private Double requestQueueWrites;

  @JsonProperty("DATA_TRANSFER_INTERNAL_GBYTES")
  private Double dataTransferInternalGbytes;

  @JsonProperty("DATA_TRANSFER_EXTERNAL_GBYTES")
  private Double dataTransferExternalGbytes;

  @JsonProperty("PROXY_RESIDENTIAL_TRANSFER_GBYTES")
  private Double proxyResidentialTransferGbytes;

  @JsonProperty("PROXY_SERPS")
  private Double proxySerps;

  /** Compute units consumed (combines CPU and memory usage over time). */
  public Double getActorComputeUnits() {
    return actorComputeUnits;
  }

  /** Number of dataset read operations. */
  public Double getDatasetReads() {
    return datasetReads;
  }

  /** Number of dataset write operations. */
  public Double getDatasetWrites() {
    return datasetWrites;
  }

  /** Number of key-value store read operations. */
  public Double getKeyValueStoreReads() {
    return keyValueStoreReads;
  }

  /** Number of key-value store write operations. */
  public Double getKeyValueStoreWrites() {
    return keyValueStoreWrites;
  }

  /** Number of key-value store list operations. */
  public Double getKeyValueStoreLists() {
    return keyValueStoreLists;
  }

  /** Number of request queue read operations. */
  public Double getRequestQueueReads() {
    return requestQueueReads;
  }

  /** Number of request queue write operations. */
  public Double getRequestQueueWrites() {
    return requestQueueWrites;
  }

  /** Internal data transfer within the Apify platform, in gigabytes. */
  public Double getDataTransferInternalGbytes() {
    return dataTransferInternalGbytes;
  }

  /** External data transfer to/from the internet, in gigabytes. */
  public Double getDataTransferExternalGbytes() {
    return dataTransferExternalGbytes;
  }

  /** Residential proxy data transfer, in gigabytes. */
  public Double getProxyResidentialTransferGbytes() {
    return proxyResidentialTransferGbytes;
  }

  /** Number of SERP (search engine results page) proxy requests. */
  public Double getProxySerps() {
    return proxySerps;
  }
}
