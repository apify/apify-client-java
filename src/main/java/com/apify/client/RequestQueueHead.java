package com.apify.client;

import java.util.Collections;
import java.util.List;

/** The head (front) of a request queue. */
public final class RequestQueueHead extends ApifyResource {
  private long limit;
  private boolean hadMultipleClients;
  private List<RequestQueueRequest> items = List.of();

  /** The maximum number of requests requested. */
  public long getLimit() {
    return limit;
  }

  /** Whether multiple clients have accessed the queue. */
  public boolean isHadMultipleClients() {
    return hadMultipleClients;
  }

  /** The requests at the head of the queue. */
  public List<RequestQueueRequest> getItems() {
    return Collections.unmodifiableList(items);
  }
}
