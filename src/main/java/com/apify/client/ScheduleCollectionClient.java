package com.apify.client;

import java.util.Iterator;

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

  /**
   * Returns a lazy iterator over the account's schedules. The options' {@code limit} caps the total
   * number yielded ({@code null} = all); {@code chunkSize} is the per-request page size ({@code
   * null} = server default).
   */
  public Iterator<Schedule> iterate(ListOptions options, Long chunkSize) {
    ListOptions opts = options != null ? options : new ListOptions();
    return ctx.iterateResource(
        "", opts.limitValue(), chunkSize, opts.offsetValue(), opts::applyFilters, Schedule.class);
  }

  /** Creates a new schedule. {@code schedule} is any JSON-serializable schedule definition. */
  public Schedule create(Object schedule) {
    return ctx.createResource(new QueryParams(), schedule, Schedule.class);
  }
}
