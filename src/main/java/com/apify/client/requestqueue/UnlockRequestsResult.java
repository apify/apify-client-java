package com.apify.client.requestqueue;

import com.apify.client.ApifyResource;

/** Returned by {@link RequestQueueClient#unlockRequests}: how many requests were unlocked. */
public final class UnlockRequestsResult extends ApifyResource {
  private long unlockedCount;

  /** The number of requests that were successfully unlocked. */
  public long getUnlockedCount() {
    return unlockedCount;
  }
}
