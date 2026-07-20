package com.apify.client.requestqueue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Confirmation that a request was successfully deleted by {@link
 * RequestQueueClient#batchDeleteRequests}, identified by whichever of {@code id}/{@code uniqueKey}
 * the API returned.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DeletedRequestInfo {
  private String id;
  private String uniqueKey;

  /** The ID of the deleted request. */
  public String getId() {
    return id;
  }

  /** The unique key of the deleted request. */
  public String getUniqueKey() {
    return uniqueKey;
  }
}
