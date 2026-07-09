package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Offline tests for the wire-value enums: the {@code fromWire} round-trip (including hyphenated and
 * dotted wire values), the {@code UNKNOWN} fallback for unrecognised values, and the end-to-end
 * Jackson deserialization path on the models that expose them. A future wire-value typo would flip
 * a real value to {@code UNKNOWN} and fail these tests rather than degrade silently.
 */
class EnumTest {

  private static ApifyClient client(MockBackend backend) {
    return ApifyClient.builder()
        .token("test-token")
        .httpBackend(backend)
        .maxRetries(0)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  @Test
  void runStatusRoundTripsAndFallsBack() {
    // Every declared status (except the null-valued UNKNOWN sentinel) must round-trip its wire
    // value.
    for (RunStatus status : RunStatus.values()) {
      if (status == RunStatus.UNKNOWN) {
        continue;
      }
      assertEquals(status, RunStatus.fromWire(status.getWireValue()));
    }
    // Hyphenated wire values are the ones most likely to break on a rename.
    assertEquals(RunStatus.TIMING_OUT, RunStatus.fromWire("TIMING-OUT"));
    assertEquals(RunStatus.TIMED_OUT, RunStatus.fromWire("TIMED-OUT"));
    assertEquals(RunStatus.UNKNOWN, RunStatus.fromWire("SOMETHING-NEW"));
    assertNull(RunStatus.fromWire(null));
    assertNull(RunStatus.UNKNOWN.getWireValue());
  }

  @Test
  void runStatusTerminality() {
    assertTrue(RunStatus.SUCCEEDED.isTerminal());
    assertTrue(RunStatus.FAILED.isTerminal());
    assertTrue(RunStatus.ABORTED.isTerminal());
    assertTrue(RunStatus.TIMED_OUT.isTerminal());
    assertFalse(RunStatus.RUNNING.isTerminal());
    assertFalse(RunStatus.TIMING_OUT.isTerminal());
    assertFalse(RunStatus.UNKNOWN.isTerminal());
  }

  @Test
  void runOriginEmitsItsWireValue() {
    // RunOrigin is write-only (only ever emitted to the origin query param), so it only needs a
    // wire-value getter; there is no fromWire/UNKNOWN read path to exercise.
    assertEquals("API", RunOrigin.API.getWireValue());
    assertEquals("SCHEDULER", RunOrigin.SCHEDULER.getWireValue());
  }

  @Test
  void webhookEventTypeRoundTripsDottedValuesAndFallsBack() {
    for (WebhookEventType eventType : WebhookEventType.values()) {
      if (eventType == WebhookEventType.UNKNOWN) {
        continue;
      }
      assertEquals(eventType, WebhookEventType.fromWire(eventType.getWireValue()));
    }
    assertEquals(
        WebhookEventType.ACTOR_RUN_SUCCEEDED, WebhookEventType.fromWire("ACTOR.RUN.SUCCEEDED"));
    assertEquals(WebhookEventType.UNKNOWN, WebhookEventType.fromWire("ACTOR.RUN.MIGRATED"));
    assertNull(WebhookEventType.fromWire(null));
  }

  @Test
  void actorRunDeserializesStatusEnumEndToEnd() {
    MockBackend terminal =
        MockBackend.ofConstant(200, "{\"data\":{\"id\":\"r1\",\"status\":\"TIMED-OUT\"}}");
    Optional<ActorRun> run = client(terminal).run("r1").get();
    assertTrue(run.isPresent());
    assertEquals(RunStatus.TIMED_OUT, run.get().getStatus());
    assertTrue(run.get().isTerminal());

    MockBackend unknown =
        MockBackend.ofConstant(200, "{\"data\":{\"id\":\"r2\",\"status\":\"WARP-DRIVE\"}}");
    ActorRun degraded = client(unknown).run("r2").get().orElseThrow();
    assertEquals(RunStatus.UNKNOWN, degraded.getStatus());
    assertFalse(degraded.isTerminal());
  }

  @Test
  void webhookDeserializesEventTypeEnumsEndToEnd() {
    MockBackend backend =
        MockBackend.ofConstant(
            200,
            "{\"data\":{\"id\":\"w1\",\"eventTypes\":[\"ACTOR.RUN.SUCCEEDED\",\"ACTOR.RUN.MIGRATED\"]}}");
    Optional<Webhook> webhook = client(backend).webhook("w1").get();
    assertTrue(webhook.isPresent());
    assertEquals(
        List.of(WebhookEventType.ACTOR_RUN_SUCCEEDED, WebhookEventType.UNKNOWN),
        webhook.get().getEventTypes());
  }
}
