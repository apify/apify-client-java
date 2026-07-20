package com.apify.client.schedule;

import com.apify.client.ApiPaths;
import com.apify.client.ListOptions;
import com.apify.client.PaginationList;
import com.apify.client.QueryParams;
import com.apify.client.ResourceContext;
import com.apify.client.http.HttpClientCore;
import java.util.Iterator;

/** A client for the schedule collection ({@code GET/POST /v2/schedules}). */
public final class ScheduleCollectionClient {
  private final ResourceContext ctx;

  public ScheduleCollectionClient(HttpClientCore http, String baseUrl) {
    this.ctx = ResourceContext.collection(http, baseUrl, ApiPaths.SCHEDULES);
  }

  /** Lists the account's schedules. */
  public PaginationList<Schedule> list(ListOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.listResource("", params, Schedule.class);
  }

  /**
   * Returns a lazy iterator over the account's schedules. The options' {@code limit} caps the total
   * number yielded ({@code null} or non-positive = all); {@code chunkSize} is the per-request page
   * size ({@code null} = server default).
   */
  public Iterator<Schedule> iterate(ListOptions options) {
    return iterate(options, null);
  }

  /** As {@link #iterate(ListOptions)}, but {@code chunkSize} sets the per-request page size. */
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
