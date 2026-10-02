package com.apify.client.schedule;

import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.Json;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import tools.jackson.databind.JavaType;

/** A client for a specific schedule ({@code /v2/schedules/{scheduleId}}). */
public final class ScheduleClient {
  private final ResourceContext ctx;

  public ScheduleClient(HttpClientCore http, String baseUrl, String id) {
    this.ctx = ResourceContext.single(http, baseUrl, ApiPaths.SCHEDULES, id);
  }

  /** Fetches the schedule, or empty if it does not exist. */
  public CompletableFuture<Optional<Schedule>> get() {
    return ctx.getResource("", new QueryParams(), Schedule.class);
  }

  /** Updates the schedule with the given fields and returns the updated object. */
  public CompletableFuture<Schedule> update(Object newFields) {
    return ctx.updateResource("", newFields, Schedule.class);
  }

  /** Deletes the schedule. */
  public CompletableFuture<Void> delete() {
    return ctx.deleteResource("");
  }

  /**
   * Fetches up to the last 1000 entries of the schedule's invocation log.
   *
   * <p>A 404 (the schedule itself no longer exists) throws {@link
   * com.apify.client.http.NotFoundError} rather than resolving to an empty list, since an empty
   * list would otherwise be indistinguishable from "no invocations yet".
   */
  public CompletableFuture<List<ScheduleInvoked>> getLog() {
    JavaType listType = Json.parametric(List.class, Json.type(ScheduleInvoked.class));
    return ctx.getResourceRequired("log", new QueryParams(), listType);
  }
}
