package com.apify.client.run;

import com.apify.client.ApifyResource;
import java.time.Instant;

/** Metadata about how an {@link ActorRun} was initiated. */
public final class ActorRunMeta extends ApifyResource {
  private String origin;
  private String clientIp;
  private String userAgent;
  private String scheduleId;
  private Instant scheduledAt;
  private String actorRunId;
  private String webhookDispatchId;
  private String scheduledAct2Id;
  private String scheduledActorTaskId;

  /** What triggered the run (e.g. {@code "WEB"}, {@code "API"}, {@code "SCHEDULER"}). */
  public String getOrigin() {
    return origin;
  }

  /** The IP address of the client that started the run, if known. */
  public String getClientIp() {
    return clientIp;
  }

  /** The {@code User-Agent} of the client that started the run. */
  public String getUserAgent() {
    return userAgent;
  }

  /** The ID of the schedule that triggered the run, if any. */
  public String getScheduleId() {
    return scheduleId;
  }

  /** When the run was scheduled, if it was triggered by a schedule. */
  public Instant getScheduledAt() {
    return scheduledAt;
  }

  /** The ID of the Actor run that started this run, if it was started by another run. */
  public String getActorRunId() {
    return actorRunId;
  }

  /** The ID of the webhook dispatch that started this run, if any. */
  public String getWebhookDispatchId() {
    return webhookDispatchId;
  }

  /** The ID of the scheduled Actor that started this run, if any. */
  public String getScheduledAct2Id() {
    return scheduledAct2Id;
  }

  /** The ID of the scheduled Actor task that started this run, if any. */
  public String getScheduledActorTaskId() {
    return scheduledActorTaskId;
  }
}
