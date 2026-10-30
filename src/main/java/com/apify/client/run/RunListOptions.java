package com.apify.client.run;

import com.apify.client.ListOptions;
import com.apify.client.internal.QueryParams;
import java.util.List;

/**
 * Run-specific filters for {@link RunCollectionClient#list(ListOptions, RunListOptions)}. The
 * {@code startedAfter}/{@code startedBefore} filters are honoured by the top-level ({@code
 * /v2/actor-runs}) and Actor-scoped ({@code /v2/actors/{actorId}/runs}) run collections; the
 * task-scoped run collection accepts only {@code status}. {@code deployment} is honoured by the
 * top-level and Actor-scoped collections.
 */
public final class RunListOptions {
  private List<String> status;
  private String startedAfter;
  private String startedBefore;
  private List<String> deployment;

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

  /**
   * Filter Actor Standby runs by the deployment pool that serves them: {@code SINGLE_TENANT},
   * {@code MULTI_TENANT_FREE_USERS}, {@code MULTI_TENANT_PAYING_USERS} or {@code
   * MULTI_TENANT_OWNER_PAID}. Sent as a comma-separated list. The API requires the {@link
   * #status(List) status} filter to be set too, with only non-terminal statuses ({@code READY},
   * {@code RUNNING}, {@code TIMING-OUT} or {@code ABORTING}).
   */
  public RunListOptions deployment(List<String> deployment) {
    this.deployment = deployment == null ? null : List.copyOf(deployment);
    return this;
  }

  void apply(QueryParams q) {
    q.addCsv("status", status)
        .addCsv("deployment", deployment)
        .addString("startedAfter", startedAfter)
        .addString("startedBefore", startedBefore);
  }
}
