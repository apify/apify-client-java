package com.apify.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The result of {@link RequestQueueClient#batchAddRequests}: accepted and unprocessed requests. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BatchAddResult {
  private List<RequestQueueOperationInfo> processedRequests = new ArrayList<>();
  private List<RequestQueueRequest> unprocessedRequests = new ArrayList<>();

  /** The requests the API successfully added (unmodifiable). */
  public List<RequestQueueOperationInfo> getProcessedRequests() {
    return Collections.unmodifiableList(processedRequests);
  }

  /** The requests the API did not process (unmodifiable). */
  public List<RequestQueueRequest> getUnprocessedRequests() {
    return Collections.unmodifiableList(unprocessedRequests);
  }

  /** Appends another result's requests into this one (used to merge per-chunk batch results). */
  void merge(BatchAddResult other) {
    this.processedRequests.addAll(other.processedRequests);
    this.unprocessedRequests.addAll(other.unprocessedRequests);
  }

  void setProcessedRequests(List<RequestQueueOperationInfo> processedRequests) {
    this.processedRequests = new ArrayList<>(processedRequests);
  }

  void setUnprocessedRequests(List<RequestQueueRequest> unprocessedRequests) {
    this.unprocessedRequests = new ArrayList<>(unprocessedRequests);
  }
}
