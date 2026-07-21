package com.apify.client.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;

/** A {@link User}'s subscription plan and its associated limits. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class UserPlan {
  private String id;
  private String description;
  private Boolean isEnabled;
  private Double monthlyBasePriceUsd;
  private Double monthlyUsageCreditsUsd;
  private Double usageDiscountPercent;
  private List<String> enabledPlatformFeatures = List.of();
  private Double maxMonthlyUsageUsd;
  private Double maxActorMemoryGbytes;
  private Double maxMonthlyActorComputeUnits;
  private Double maxMonthlyResidentialProxyGbytes;
  private Double maxMonthlyProxySerps;
  private Double maxMonthlyExternalDataTransferGbytes;
  private Long maxActorCount;
  private Long maxActorTaskCount;
  private Long dataRetentionDays;
  private JsonNode availableProxyGroups;
  private Long teamAccountSeatCount;
  private String supportLevel;
  private List<JsonNode> availableAddOns = List.of();

  /** The plan's identifier. */
  public String getId() {
    return id;
  }

  /** A human-readable description of the plan. */
  public String getDescription() {
    return description;
  }

  /** Whether the plan is currently active. */
  public Boolean getIsEnabled() {
    return isEnabled;
  }

  /** The plan's monthly base price, in USD. */
  public Double getMonthlyBasePriceUsd() {
    return monthlyBasePriceUsd;
  }

  /** The USD value of usage credits included in the plan each month. */
  public Double getMonthlyUsageCreditsUsd() {
    return monthlyUsageCreditsUsd;
  }

  /** The discount percentage applied to usage beyond the included credits. */
  public Double getUsageDiscountPercent() {
    return usageDiscountPercent;
  }

  /**
   * The platform features enabled by this plan (e.g. {@code "ACTORS"}, {@code "PROXY"};
   * unmodifiable, never {@code null}).
   */
  public List<String> getEnabledPlatformFeatures() {
    return enabledPlatformFeatures == null
        ? List.of()
        : Collections.unmodifiableList(enabledPlatformFeatures);
  }

  /** The plan's total monthly usage cap, in USD. */
  public Double getMaxMonthlyUsageUsd() {
    return maxMonthlyUsageUsd;
  }

  /** The maximum memory, in gigabytes, a single Actor run may use under this plan. */
  public Double getMaxActorMemoryGbytes() {
    return maxActorMemoryGbytes;
  }

  /** The plan's monthly Actor compute unit cap. */
  public Double getMaxMonthlyActorComputeUnits() {
    return maxMonthlyActorComputeUnits;
  }

  /** The plan's monthly residential proxy data-transfer cap, in gigabytes. */
  public Double getMaxMonthlyResidentialProxyGbytes() {
    return maxMonthlyResidentialProxyGbytes;
  }

  /** The plan's monthly SERP proxy request cap. */
  public Double getMaxMonthlyProxySerps() {
    return maxMonthlyProxySerps;
  }

  /** The plan's monthly external data-transfer cap, in gigabytes. */
  public Double getMaxMonthlyExternalDataTransferGbytes() {
    return maxMonthlyExternalDataTransferGbytes;
  }

  /** The maximum number of Actors this plan allows the user to own. */
  public Long getMaxActorCount() {
    return maxActorCount;
  }

  /** The maximum number of tasks this plan allows the user to own. */
  public Long getMaxActorTaskCount() {
    return maxActorTaskCount;
  }

  /** How many days storage data is retained under this plan. */
  public Long getDataRetentionDays() {
    return dataRetentionDays;
  }

  /**
   * The proxy groups available under this plan and their counts, as raw JSON (dynamic group-name
   * keys).
   */
  public JsonNode getAvailableProxyGroups() {
    return availableProxyGroups;
  }

  /** The number of team-account seats included in this plan. */
  public Long getTeamAccountSeatCount() {
    return teamAccountSeatCount;
  }

  /** The plan's support tier. */
  public String getSupportLevel() {
    return supportLevel;
  }

  /**
   * Add-ons available under this plan, as raw JSON (shape varies per add-on; unmodifiable, never
   * {@code null}).
   */
  public List<JsonNode> getAvailableAddOns() {
    return availableAddOns == null ? List.of() : Collections.unmodifiableList(availableAddOns);
  }
}
