package com.apify.client.requestqueue;

import com.apify.client.ApifyResource;
import java.time.Instant;

/** Returned by {@link RequestQueueClient#prolongRequestLock}: the request's new lock expiry. */
public final class RequestLockInfo extends ApifyResource {
  private Instant lockExpiresAt;

  /** When the (extended) lock on the request expires. */
  public Instant getLockExpiresAt() {
    return lockExpiresAt;
  }
}
