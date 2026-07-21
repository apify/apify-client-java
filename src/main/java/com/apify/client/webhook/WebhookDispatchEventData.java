package com.apify.client.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The event payload that triggered a {@link WebhookDispatch}. Fields not relevant to the triggering
 * event are {@code null}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WebhookDispatchEventData {
  private String actorRunId;
  private String actorId;
  private String actorTaskId;
  private String actorBuildId;

  /** The ID of the Actor run that triggered the event, if any. */
  public String getActorRunId() {
    return actorRunId;
  }

  /** The ID of the Actor that triggered the event, if any. */
  public String getActorId() {
    return actorId;
  }

  /** The ID of the task that triggered the event, if any. */
  public String getActorTaskId() {
    return actorTaskId;
  }

  /** The ID of the build that triggered the event, if any. */
  public String getActorBuildId() {
    return actorBuildId;
  }
}
