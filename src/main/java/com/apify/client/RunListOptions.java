package com.apify.client;

import java.util.List;

/**
 * Run-specific filters for {@link RunCollectionClient#list(ListOptions, RunListOptions)}. The
 * {@code startedAfter}/{@code startedBefore} filters are only honoured by the Actor-scoped and
 * task-scoped run collections.
 */
public final class RunListOptions {
  private List<String> status;
  private String startedAfter;
  private String startedBefore;

  /**
   * Filter by one or more run statuses (e.g. {@code "SUCCEEDED"}, {@code "RUNNING"}). Sent as a
   * comma-separated list, as the API accepts.
   */
  public RunListOptions status(List<String> status) {
    this.status = status == null ? null : List.copyOf(status);
    return this;
  }

  /** Filter to runs started after this ISO-8601 timestamp. */
  public RunListOptions startedAfter(String startedAfter) {
    this.startedAfter = startedAfter;
    return this;
  }

  /** Filter to runs started before this ISO-8601 timestamp. */
  public RunListOptions startedBefore(String startedBefore) {
    this.startedBefore = startedBefore;
    return this;
  }

  void apply(QueryParams q) {
    q.addCsv("status", status)
        .addString("startedAfter", startedAfter)
        .addString("startedBefore", startedBefore);
  }
}
