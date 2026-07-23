package com.apify.client.requestqueue;

import com.apify.client.ApifyResource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The result of {@link RequestQueueClient#batchAddRequests}: accepted and unprocessed requests. */
public final class BatchAddResult extends ApifyResource {
  private List<RequestQueueOperationInfo> processedRequests = new ArrayList<>();
  private List<RequestQueueRequest> unprocessedRequests = new ArrayList<>();

  /** The requests the API successfully added (unmodifiable). */
  public List<RequestQueueOperationInfo> getProcessedRequests() {
    return Collections.unmodifiableList(processedRequests == null ? List.of() : processedRequests);
  }

  /** The requests the API did not process (unmodifiable). */
  public List<RequestQueueRequest> getUnprocessedRequests() {
    return Collections.unmodifiableList(
        unprocessedRequests == null ? List.of() : unprocessedRequests);
  }

  /**
   * Appends another result's requests into this one (used to merge per-chunk batch results).
   * Tolerates {@code other}'s lists being {@code null} (Jackson assigns the field directly on an
   * explicit {@code "processedRequests": null}/{@code "unprocessedRequests": null} response,
   * bypassing the constructor's empty-list default), mirroring {@link #getProcessedRequests()}/
   * {@link #getUnprocessedRequests()}'s own null-coalescing.
   */
  void merge(BatchAddResult other) {
    if (other.processedRequests != null) {
      this.processedRequests.addAll(other.processedRequests);
    }
    if (other.unprocessedRequests != null) {
      this.unprocessedRequests.addAll(other.unprocessedRequests);
    }
  }

  void setProcessedRequests(List<RequestQueueOperationInfo> processedRequests) {
    this.processedRequests = new ArrayList<>(processedRequests);
  }

  void setUnprocessedRequests(List<RequestQueueRequest> unprocessedRequests) {
    this.unprocessedRequests = new ArrayList<>(unprocessedRequests);
  }
}
