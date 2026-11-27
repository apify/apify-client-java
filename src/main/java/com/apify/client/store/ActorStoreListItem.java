package com.apify.client.store;

import com.apify.client.ApifyResource;
import com.apify.client.internal.NormalizedUrlDeserializer;
import java.util.Collections;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

/** An Actor as listed in the Apify Store. */
public final class ActorStoreListItem extends ApifyResource {
  private String id;
  private String name;
  private String username;
  private String title;
  private String userFullName;
  private String description;
  private List<String> categories = List.of();
  private String notice;
  private StoreActorStats stats;
  private PricingInfo currentPricingInfo;
  private Boolean isWhiteListedForAgenticPayments;
  private Boolean isVerified;
  private String badge;
  private String pictureUrl;

  @JsonDeserialize(using = NormalizedUrlDeserializer.class)
  private String userPictureUrl;

  @JsonDeserialize(using = NormalizedUrlDeserializer.class)
  private String url;

  private String readmeSummary;

  /** The unique Actor ID. */
  public String getId() {
    return id;
  }

  /** The technical name of the Actor. */
  public String getName() {
    return name;
  }

  /** The username of the Actor's owner. */
  public String getUsername() {
    return username;
  }

  /** The human-readable title. */
  public String getTitle() {
    return title;
  }

  /** The full name of the Actor's owner, if set. */
  public String getUserFullName() {
    return userFullName;
  }

  /** A description of what the Actor does. */
  public String getDescription() {
    return description;
  }

  /**
   * The IDs of the Store categories the Actor belongs to, e.g. {@code "AI_AND_ML"}. Empty when the
   * Actor has none.
   */
  public List<String> getCategories() {
    return categories == null ? List.of() : Collections.unmodifiableList(categories);
  }

  /**
   * A warning displayed on the Actor's Store page: {@code "NONE"}, {@code
   * "RESIDENTIAL_PROXY_REQUIRED"} or {@code "UNDER_MAINTENANCE"}. Can be set by the Actor's
   * developer or automatically by Apify's quality checks.
   */
  public String getNotice() {
    return notice;
  }

  /** Usage statistics and Apify Store metrics of the Actor. */
  public StoreActorStats getStats() {
    return stats;
  }

  /** The Actor's current pricing model. */
  public PricingInfo getCurrentPricingInfo() {
    return currentPricingInfo;
  }

  /** Whether the Actor is whitelisted for agentic payment processing. */
  public Boolean getIsWhiteListedForAgenticPayments() {
    return isWhiteListedForAgenticPayments;
  }

  /** Whether the Actor's developer has a verified identity. */
  public Boolean getIsVerified() {
    return isVerified;
  }

  /** The badge shown for the Actor in the Store, if any. */
  public String getBadge() {
    return badge;
  }

  /** URL of the Actor's picture, if set. */
  public String getPictureUrl() {
    return pictureUrl;
  }

  /** URL of the Actor owner's profile picture, if set. */
  public String getUserPictureUrl() {
    return userPictureUrl;
  }

  /** The URL of the Actor's public Store page. */
  public String getUrl() {
    return url;
  }

  /** A brief, LLM-generated summary of the Actor's README. */
  public String getReadmeSummary() {
    return readmeSummary;
  }
}
