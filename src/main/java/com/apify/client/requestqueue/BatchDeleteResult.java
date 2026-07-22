package com.apify.client.requestqueue;

import com.apify.client.ApifyResource;
import java.util.Collections;
import java.util.List;

/** The result of {@link RequestQueueClient#batchDeleteRequests}: deleted and failed requests. */
public final class BatchDeleteResult extends ApifyResource {
  private List<DeletedRequestInfo> processedRequests = List.of();
  private List<RequestQueueRequest> unprocessedRequests = List.of();

  /** The requests the API successfully deleted (unmodifiable). */
  public List<DeletedRequestInfo> getProcessedRequests() {
    return Collections.unmodifiableList(processedRequests == null ? List.of() : processedRequests);
  }

  /** The requests the API did not delete, e.g. due to rate limiting (unmodifiable). */
  public List<RequestQueueRequest> getUnprocessedRequests() {
    return Collections.unmodifiableList(
        unprocessedRequests == null ? List.of() : unprocessedRequests);
  }
}
