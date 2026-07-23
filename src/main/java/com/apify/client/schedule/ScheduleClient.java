package com.apify.client.schedule;

import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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

  /** Fetches the schedule's invocation log as text, or empty if absent. */
  public CompletableFuture<Optional<String>> getLog() {
    return ctx.getRaw("log", new QueryParams())
        .thenApply(
            resp ->
                resp == null
                    ? Optional.empty()
                    : Optional.of(new String(resp.body(), StandardCharsets.UTF_8)));
  }
}
