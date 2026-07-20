package com.apify.client.requestqueue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Returned by {@link RequestQueueClient#unlockRequests}: how many requests were unlocked. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class UnlockRequestsResult {
  private long unlockedCount;

  /** The number of requests that were successfully unlocked. */
  public long getUnlockedCount() {
    return unlockedCount;
  }
}
