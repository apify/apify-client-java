package com.apify.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * An event that can trigger a webhook (the API's {@code WebhookEventType}).
 *
 * <p>{@link #UNKNOWN} is returned for any event type the API introduces that this client version
 * does not yet recognise, so deserialization never fails on a newer server.
 */
public enum WebhookEventType {
  /** An Actor run was created. */
  ACTOR_RUN_CREATED("ACTOR.RUN.CREATED"),
  /** An Actor run succeeded. */
  ACTOR_RUN_SUCCEEDED("ACTOR.RUN.SUCCEEDED"),
  /** An Actor run failed. */
  ACTOR_RUN_FAILED("ACTOR.RUN.FAILED"),
  /** An Actor run was aborted. */
  ACTOR_RUN_ABORTED("ACTOR.RUN.ABORTED"),
  /** An Actor run timed out. */
  ACTOR_RUN_TIMED_OUT("ACTOR.RUN.TIMED_OUT"),
  /** An Actor run was resurrected. */
  ACTOR_RUN_RESURRECTED("ACTOR.RUN.RESURRECTED"),
  /** An Actor build was created. */
  ACTOR_BUILD_CREATED("ACTOR.BUILD.CREATED"),
  /** An Actor build succeeded. */
  ACTOR_BUILD_SUCCEEDED("ACTOR.BUILD.SUCCEEDED"),
  /** An Actor build failed. */
  ACTOR_BUILD_FAILED("ACTOR.BUILD.FAILED"),
  /** An Actor build was aborted. */
  ACTOR_BUILD_ABORTED("ACTOR.BUILD.ABORTED"),
  /** An Actor build timed out. */
  ACTOR_BUILD_TIMED_OUT("ACTOR.BUILD.TIMED_OUT"),
  /** A test event, sent when a webhook is tested. */
  TEST("TEST"),
  /** An event type the API returned that this client version does not recognise. */
  UNKNOWN(null);

  private final String wireValue;

  WebhookEventType(String wireValue) {
    this.wireValue = wireValue;
  }

  /**
   * The value used on the wire (e.g. {@code "ACTOR.RUN.SUCCEEDED"}); {@code null} for {@link
   * #UNKNOWN}.
   */
  @JsonValue
  public String getWireValue() {
    return wireValue;
  }

  /** Maps a wire value to its constant, or {@link #UNKNOWN} for an unrecognised non-null value. */
  @JsonCreator
  public static WebhookEventType fromWire(String value) {
    if (value == null) {
      return null;
    }
    for (WebhookEventType eventType : values()) {
      if (value.equals(eventType.wireValue)) {
        return eventType;
      }
    }
    return UNKNOWN;
  }
}
