package com.apify.client.store;

import com.apify.client.ApifyResource;

/** Actor usage statistics and Apify Store metrics returned by the Store endpoint. */
public final class StoreActorStats extends ApifyResource {
  private Long totalUsers;
  private Long totalRuns;
  private Long totalUsers7Days;
  private Long totalUsers30Days;
  private Long totalUsers90Days;
  private Double actorReviewRating;
  private Long actorReviewCount;

  /** The total number of Actor users, including its owner. */
  public Long getTotalUsers() {
    return totalUsers;
  }

  /** The total number of runs of the Actor. */
  public Long getTotalRuns() {
    return totalRuns;
  }

  /** The number of active users of the Actor in the last 7 days. */
  public Long getTotalUsers7Days() {
    return totalUsers7Days;
  }

  /** The number of active users of the Actor in the last 30 days. */
  public Long getTotalUsers30Days() {
    return totalUsers30Days;
  }

  /** The number of active users of the Actor in the last 90 days. */
  public Long getTotalUsers90Days() {
    return totalUsers90Days;
  }

  /** The average rating of the Actor in Apify Store. */
  public Double getActorReviewRating() {
    return actorReviewRating;
  }

  /** The number of reviews the Actor has received in Apify Store. */
  public Long getActorReviewCount() {
    return actorReviewCount;
  }
}
