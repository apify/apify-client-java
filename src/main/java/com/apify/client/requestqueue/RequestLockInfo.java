package com.apify.client.requestqueue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** Returned by {@link RequestQueueClient#prolongRequestLock}: the request's new lock expiry. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RequestLockInfo {
  private Instant lockExpiresAt;

  /** When the (extended) lock on the request expires. */
  public Instant getLockExpiresAt() {
    return lockExpiresAt;
  }
}
