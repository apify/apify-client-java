package com.apify.client.requestqueue;

import com.apify.client.ApifyResource;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** The head (front) of a request queue. */
public final class RequestQueueHead extends ApifyResource {
  private long limit;
  private Instant queueModifiedAt;
  private boolean hadMultipleClients;
  private List<RequestQueueRequest> items = List.of();

  /** The maximum number of requests requested. */
  public long getLimit() {
    return limit;
  }

  /** When the queue was last modified (add/update/remove/lock/unlock of any request). */
  public Instant getQueueModifiedAt() {
    return queueModifiedAt;
  }

  /** Whether multiple clients have accessed the queue. */
  public boolean isHadMultipleClients() {
    return hadMultipleClients;
  }

  /** The requests at the head of the queue (never {@code null}; unmodifiable). */
  public List<RequestQueueRequest> getItems() {
    // Null-coalesce: Jackson binds directly to the (private) `items` field for deserialization,
    // which bypasses the `= List.of()` field initializer whenever the API response contains an
    // explicit `"items": null`.
    return items == null ? List.of() : Collections.unmodifiableList(items);
  }
}
