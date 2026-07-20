package com.apify.client.schedule;

import com.apify.client.internal.ApiResponse;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import com.apify.client.internal.ResourcePaths;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** A client for a specific schedule ({@code /v2/schedules/{scheduleId}}). */
public final class ScheduleClient {
  private final ResourceContext ctx;

  public ScheduleClient(HttpClientCore http, String baseUrl, String id) {
    this.ctx = ResourceContext.single(http, baseUrl, ResourcePaths.SCHEDULES, id);
  }

  /** Fetches the schedule, or empty if it does not exist. */
  public Optional<Schedule> get() {
    return ctx.getResource("", new QueryParams(), Schedule.class);
  }

  /** Updates the schedule with the given fields and returns the updated object. */
  public Schedule update(Object newFields) {
    return ctx.updateResource("", newFields, Schedule.class);
  }

  /** Deletes the schedule. */
  public void delete() {
    ctx.deleteResource("");
  }

  /** Fetches the schedule's invocation log as text, or empty if absent. */
  public Optional<String> getLog() {
    ApiResponse resp = ctx.getRaw("log", new QueryParams());
    if (resp == null) {
      return Optional.empty();
    }
    return Optional.of(new String(resp.body, StandardCharsets.UTF_8));
  }
}
