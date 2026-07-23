package com.apify.client.store;

import com.apify.client.ApifyResource;
import com.apify.client.actor.ActorStats;

/** An Actor as listed in the Apify Store. */
public final class ActorStoreListItem extends ApifyResource {
  private String id;
  private String name;
  private String username;
  private String title;
  private String description;
  private ActorStats stats;
  private PricingInfo currentPricingInfo;
  private String pictureUrl;
  private String userPictureUrl;
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

  /** A description of what the Actor does. */
  public String getDescription() {
    return description;
  }

  /** Usage and run statistics for the Actor. */
  public ActorStats getStats() {
    return stats;
  }

  /** The Actor's current pricing model. */
  public PricingInfo getCurrentPricingInfo() {
    return currentPricingInfo;
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
