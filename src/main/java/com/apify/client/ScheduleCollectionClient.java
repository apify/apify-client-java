package com.apify.client;

/** A client for the schedule collection ({@code GET/POST /v2/schedules}). */
public final class ScheduleCollectionClient {
  private final ResourceContext ctx;

  ScheduleCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, "schedules");
  }

  /** Lists the account's schedules. */
  public PaginationList<Schedule> list(ListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, Schedule.class);
  }

  /** Creates a new schedule. {@code schedule} is any JSON-serializable schedule definition. */
  public Schedule create(Object schedule) {
    return ctx.createResource(new QueryParams(), schedule, Schedule.class);
  }
}
