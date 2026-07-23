package com.apify.client.requestqueue;

import com.apify.client.ApifyResource;

/** Returned when adding or updating a request in a queue. */
public final class RequestQueueOperationInfo extends ApifyResource {
  private String requestId;
  private String uniqueKey;
  private boolean wasAlreadyPresent;
  private boolean wasAlreadyHandled;

  /** The ID of the affected request. */
  public String getRequestId() {
    return requestId;
  }

  /**
   * The unique key of the affected request. Populated for batch-add results; may be {@code null}
   * for single add/update operations.
   */
  public String getUniqueKey() {
    return uniqueKey;
  }

  /** Whether the request was already in the queue. */
  public boolean isWasAlreadyPresent() {
    return wasAlreadyPresent;
  }

  /** Whether the request had already been handled. */
  public boolean isWasAlreadyHandled() {
    return wasAlreadyHandled;
  }
}
