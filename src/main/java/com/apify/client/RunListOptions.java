package com.apify.client;

import java.util.List;
import java.util.Objects;

/**
 * Run-specific filters for {@link RunCollectionClient#list(ListOptions, RunListOptions)}. The
 * {@code startedAfter}/{@code startedBefore} filters are only honoured by the Actor-scoped and
 * task-scoped run collections.
 */
public final class RunListOptions {
  private List<RunStatus> status;
  private String startedAfter;
  private String startedBefore;

  /**
   * Filter by one or more run statuses (e.g. {@link RunStatus#SUCCEEDED}, {@link
   * RunStatus#RUNNING}). Sent as a comma-separated list, as the API accepts.
   */
  public RunListOptions status(List<RunStatus> status) {
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
    // Filter out null wire values (e.g. RunStatus.UNKNOWN, the read-only sentinel) so the
    // parse-only fallback can never leak a literal "null" into the status query parameter.
    List<String> statusValues =
        status == null
            ? null
            : status.stream().map(RunStatus::getWireValue).filter(Objects::nonNull).toList();
    q.addCsv("status", statusValues)
        .addString("startedAfter", startedAfter)
        .addString("startedBefore", startedBefore);
  }
}
