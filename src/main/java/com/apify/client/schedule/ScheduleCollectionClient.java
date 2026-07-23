package com.apify.client.schedule;

import com.apify.client.ListOptions;
import com.apify.client.internal.AbstractCollectionClient;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.concurrent.CompletableFuture;

/** A client for the schedule collection ({@code GET/POST /v2/schedules}). */
public final class ScheduleCollectionClient
    extends AbstractCollectionClient<Schedule, ListOptions> {

  public ScheduleCollectionClient(HttpClientCore http, String baseUrl) {
    super(
        ResourceContext.collection(http, baseUrl, ApiPaths.SCHEDULES),
        Schedule.class,
        ListOptions::new);
  }

  /** Creates a new schedule. {@code schedule} is any JSON-serializable schedule definition. */
  public CompletableFuture<Schedule> create(Object schedule) {
    return ctx.createResource(new QueryParams(), schedule, Schedule.class);
  }
}
