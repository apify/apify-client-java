package com.apify.client.requestqueue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

/**
 * A page of requests returned by {@link RequestQueueClient#listRequests}, with cursor-based
 * pagination information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RequestsList {
  private List<RequestQueueRequest> items = List.of();
  private long limit;

  /**
   * @deprecated Use {@link #getCursor()}/{@link #getNextCursor()} for pagination instead.
   */
  @Deprecated private String exclusiveStartId;

  private String cursor;
  private String nextCursor;

  /** The requests in this page (unmodifiable). */
  public List<RequestQueueRequest> getItems() {
    return Collections.unmodifiableList(items);
  }

  /** The maximum number of requests requested for this page. */
  public long getLimit() {
    return limit;
  }

  /**
   * The ID of the last request from the previous page, if this page was requested that way.
   *
   * @deprecated Use {@link #getCursor()}/{@link #getNextCursor()} for pagination instead.
   */
  @Deprecated
  public String getExclusiveStartId() {
    return exclusiveStartId;
  }

  /** The cursor identifying this page, if this page was requested by cursor. */
  public String getCursor() {
    return cursor;
  }

  /** The cursor to pass to fetch the next page, or {@code null}/empty if this is the last page. */
  public String getNextCursor() {
    return nextCursor;
  }
}
