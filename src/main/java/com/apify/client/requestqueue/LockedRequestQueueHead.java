package com.apify.client.requestqueue;

import com.apify.client.ApifyResource;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * The head (front) of a request queue, atomically locked for processing by {@link
 * RequestQueueClient#listAndLockHead}. Each item's {@link RequestQueueRequest#getLockExpiresAt()}
 * reports its individual lock expiry.
 */
public final class LockedRequestQueueHead extends ApifyResource {
  private long limit;
  private Instant queueModifiedAt;
  private boolean hadMultipleClients;
  private boolean queueHasLockedRequests;
  private String clientKey;
  private long lockSecs;
  private List<RequestQueueRequest> items = List.of();

  /** The maximum number of requests requested. */
  public long getLimit() {
    return limit;
  }

  /** When the queue was last modified. */
  public Instant getQueueModifiedAt() {
    return queueModifiedAt;
  }

  /** Whether multiple clients have accessed the queue. */
  public boolean isHadMultipleClients() {
    return hadMultipleClients;
  }

  /** Whether the queue has requests locked by any client (this one or a different one). */
  public boolean isQueueHasLockedRequests() {
    return queueHasLockedRequests;
  }

  /** The client key used for locking the returned requests. */
  public String getClientKey() {
    return clientKey;
  }

  /** The number of seconds the locks are held for. */
  public long getLockSecs() {
    return lockSecs;
  }

  /** The locked requests at the head of the queue. */
  public List<RequestQueueRequest> getItems() {
    return Collections.unmodifiableList(items);
  }
}
