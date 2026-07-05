package com.apify.client;

import java.util.List;

/** Configures {@link RequestQueueClient#listRequests(ListRequestsOptions)}. */
public final class ListRequestsOptions {

  /** Filter value: currently locked requests. */
  public static final String FILTER_LOCKED = "locked";

  /** Filter value: pending (not-yet-handled) requests. */
  public static final String FILTER_PENDING = "pending";

  private Long limit;
  private String exclusiveStartId;
  private String cursor;
  private List<String> filter;

  /** Maximum number of requests to return. */
  public ListRequestsOptions limit(Long limit) {
    this.limit = limit;
    return this;
  }

  /** List requests after this ID. */
  public ListRequestsOptions exclusiveStartId(String exclusiveStartId) {
    this.exclusiveStartId = exclusiveStartId;
    return this;
  }

  /** An opaque pagination cursor (alternative to {@code exclusiveStartId}). */
  public ListRequestsOptions cursor(String cursor) {
    this.cursor = cursor;
    return this;
  }

  /**
   * Restrict the listing to requests in the given states. Each value must be {@link #FILTER_LOCKED}
   * or {@link #FILTER_PENDING}; multiple values are sent comma-separated and mean the union of
   * those states.
   */
  public ListRequestsOptions filter(List<String> filter) {
    this.filter = filter == null ? null : List.copyOf(filter);
    return this;
  }

  /** Validates the options for API-level constraints. */
  void validate() {
    if (exclusiveStartId != null && cursor != null) {
      throw new IllegalArgumentException(
          "ListRequestsOptions: exclusiveStartId and cursor are mutually exclusive");
    }
    if (filter != null) {
      for (String f : filter) {
        if (!FILTER_LOCKED.equals(f) && !FILTER_PENDING.equals(f)) {
          throw new IllegalArgumentException(
              "ListRequestsOptions: filter entries must be \""
                  + FILTER_LOCKED
                  + "\" or \""
                  + FILTER_PENDING
                  + "\", got \""
                  + f
                  + "\"");
        }
      }
    }
  }

  void apply(QueryParams q) {
    q.addLong("limit", limit)
        .addString("exclusiveStartId", exclusiveStartId)
        .addString("cursor", cursor)
        .addCsv("filter", filter);
  }
}
