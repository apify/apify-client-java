package com.apify.client.requestqueue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

/** The result of {@link RequestQueueClient#batchDeleteRequests}: deleted and failed requests. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BatchDeleteResult {
  private List<DeletedRequestInfo> processedRequests = List.of();
  private List<RequestQueueRequest> unprocessedRequests = List.of();

  /** The requests the API successfully deleted (unmodifiable). */
  public List<DeletedRequestInfo> getProcessedRequests() {
    return Collections.unmodifiableList(processedRequests);
  }

  /** The requests the API did not delete, e.g. due to rate limiting (unmodifiable). */
  public List<RequestQueueRequest> getUnprocessedRequests() {
    return Collections.unmodifiableList(unprocessedRequests);
  }
}
