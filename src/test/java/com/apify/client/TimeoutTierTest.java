package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.actor.ActorStartOptions;
import com.apify.client.http.ApifyTransportException;
import com.apify.client.http.Timeout;
import com.apify.client.keyvalue.SetRecordOptions;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/**
 * Offline tests for the timeout tiers: which tier each kind of method picks, how the builder
 * configures the tiers and the cap, how {@link ApifyClient#withTimeout(Timeout)} overrides them,
 * and how a retry grows the timeout of an attempt.
 */
class TimeoutTierTest {

  private static final String RUN_JSON = "{\"data\":{\"id\":\"r1\",\"status\":\"RUNNING\"}}";

  private static ApifyClientBuilder builder(MockTransport backend) {
    return ApifyClient.builder()
        .token("t")
        .httpTransport(backend)
        .maxRetries(0)
        .minDelayBetweenRetries(Duration.ofMillis(1));
  }

  private static ApifyClient client(MockTransport backend) {
    return builder(backend).build();
  }

  private static Optional<Duration> secs(long seconds) {
    return Optional.of(Duration.ofSeconds(seconds));
  }

  @Test
  void metadataReadUsesTheShortTier() {
    MockTransport backend = MockTransport.ofConstant(200, RUN_JSON);
    client(backend).run("r1").get().join();
    assertEquals(secs(5), backend.lastTimeout);
  }

  @Test
  void listingUsesTheMediumTier() {
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":{\"items\":[],\"total\":0}}");
    client(backend).actors().list(new com.apify.client.actor.ActorListOptions()).join();
    assertEquals(secs(30), backend.lastTimeout);
  }

  @Test
  void downloadsAndRecordReadsUseTheLongTier() {
    MockTransport backend = MockTransport.ofConstant(200, "[]");
    client(backend)
        .dataset("d1")
        .listItems(new com.apify.client.dataset.DatasetListItemsOptions())
        .join();
    assertEquals(secs(360), backend.lastTimeout);

    backend = MockTransport.ofConstant(200, "abc");
    client(backend).keyValueStore("s1").getRecord("k").join();
    assertEquals(secs(360), backend.lastTimeout);
  }

  @Test
  void keyValueStoreUpdateAndRequestQueueUnlockFollowTheReferenceTiers() {
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":{\"id\":\"s1\"}}");
    client(backend).keyValueStore("s1").update(java.util.Map.of("name", "x")).join();
    assertEquals(secs(360), backend.lastTimeout);

    backend = MockTransport.ofConstant(200, "{\"data\":{\"unlockedCount\":0}}");
    client(backend).requestQueue("q1").unlockRequests().join();
    assertEquals(secs(360), backend.lastTimeout);
  }

  @Test
  void startIsMediumExtendedByTheWaitForFinishHold() {
    MockTransport backend = MockTransport.ofConstant(201, RUN_JSON);
    client(backend).actor("a1").start(null, new ActorStartOptions()).join();
    assertEquals(secs(30), backend.lastTimeout, "no hold: the plain medium tier");

    client(backend).actor("a1").start(null, new ActorStartOptions().waitForFinish(20L)).join();
    assertEquals(secs(50), backend.lastTimeout, "medium tier plus the 20s hold");

    client(backend).actor("a1").start(null, new ActorStartOptions().waitForFinish(500L)).join();
    assertEquals(secs(90), backend.lastTimeout, "the hold is capped at the API's 60s");
  }

  @Test
  void getWithWaitIsShortExtendedByTheHold() {
    MockTransport backend = MockTransport.ofConstant(200, RUN_JSON);
    client(backend).run("r1").getWithWait(30L).join();
    assertTrue(backend.lastUrl.contains("waitForFinish=30"), backend.lastUrl);
    assertEquals(secs(35), backend.lastTimeout);
  }

  @Test
  void waitForFinishPollsHaveNoTimeoutAndHoldForTheRemainingBudget() {
    MockTransport backend =
        MockTransport.ofConstant(200, "{\"data\":{\"id\":\"r1\",\"status\":\"SUCCEEDED\"}}");
    client(backend).run("r1").waitForFinish(120L).join();
    assertEquals(Optional.empty(), backend.lastTimeout, "polls run without a timeout");
    assertTrue(backend.lastUrl.contains("waitForFinish=60"), backend.lastUrl);
  }

  @Test
  void callSendsTheStartWithoutATimeout() {
    MockTransport backend =
        MockTransport.ofRouter(
            request ->
                request.method().equals("POST")
                    ? MockTransport.ok(201, RUN_JSON)
                    : MockTransport.ok(200, "{\"data\":{\"id\":\"r1\",\"status\":\"SUCCEEDED\"}}"));
    client(backend).actor("a1").call(null, new ActorStartOptions(), 10L).join();
    assertEquals(Optional.empty(), backend.timeouts.get(0), "the start inside call()");
  }

  @Test
  void builderConfiguresEveryTier() {
    MockTransport backend = MockTransport.ofConstant(200, RUN_JSON);
    ApifyClient client =
        builder(backend)
            .timeoutShort(Duration.ofSeconds(2))
            .timeoutMedium(Duration.ofSeconds(7))
            .timeoutLong(Duration.ofSeconds(90))
            .build();
    client.run("r1").get().join();
    assertEquals(secs(2), backend.lastTimeout);
    client.runs().list(new com.apify.client.ListOptions(), null).join();
    assertEquals(secs(7), backend.lastTimeout);
    client.keyValueStore("s").getRecord("k").join();
    assertEquals(secs(90), backend.lastTimeout);
  }

  @Test
  void aTierAboveTimeoutMaxIsCapped() {
    MockTransport backend = MockTransport.ofConstant(200, RUN_JSON);
    ApifyClient client =
        builder(backend)
            .timeoutShort(Duration.ofMinutes(10))
            .timeoutMax(Duration.ofMinutes(1))
            .build();
    client.run("r1").get().join();
    assertEquals(secs(60), backend.lastTimeout);
  }

  @Test
  void withTimeoutReplacesTheTierOfEveryRequest() {
    MockTransport backend = MockTransport.ofConstant(200, RUN_JSON);
    ApifyClient client = client(backend);

    client.withTimeout(Timeout.ofSeconds(120)).run("r1").get().join();
    assertEquals(secs(120), backend.lastTimeout);

    client.withTimeout(Timeout.LONG).run("r1").get().join();
    assertEquals(secs(360), backend.lastTimeout);

    client.withTimeout(Timeout.NO_TIMEOUT).run("r1").get().join();
    assertEquals(Optional.empty(), backend.lastTimeout);

    client.run("r1").get().join();
    assertEquals(secs(5), backend.lastTimeout, "the original client keeps its own tiers");
  }

  @Test
  void withTimeoutIsKeptAndTheHoldIsCutToWhatItCovers() {
    MockTransport backend = MockTransport.ofConstant(200, RUN_JSON);
    client(backend).withTimeout(Timeout.ofSeconds(10)).run("r1").getWithWait(30L).join();
    assertEquals(secs(10), backend.lastTimeout, "the override is used as given");
    assertTrue(
        backend.lastUrl.contains("waitForFinish=5"), "10s less the 5s margin: " + backend.lastUrl);
  }

  @Test
  void aHoldIsCutToWhatTimeoutMaxLeavesAboveTheTier() {
    MockTransport backend = MockTransport.ofConstant(200, RUN_JSON);
    ApifyClient client = builder(backend).timeoutMax(Duration.ofSeconds(12)).build();

    client.run("r1").getWithWait(30L).join();
    assertTrue(backend.lastUrl.contains("waitForFinish=7"), "5s tier, 12s cap: " + backend.lastUrl);
    assertEquals(secs(12), backend.lastTimeout, "the request is not given up before the hold ends");

    client.actor("a1").start(null, new ActorStartOptions().waitForFinish(60L)).join();
    assertTrue(
        backend.lastUrl.contains("waitForFinish=7"),
        "the 30s medium tier is capped at 12s: the hold is cut to the cap less the margin: "
            + backend.lastUrl);
    assertEquals(secs(12), backend.lastTimeout);
  }

  @Test
  void startKeepsWaitForFinishUnderTheDeprecatedTimeoutAlias() {
    MockTransport backend = MockTransport.ofConstant(200, RUN_JSON);
    ApifyClient client = builder(backend).timeout(Duration.ofSeconds(30)).build();

    client.actor("a1").start(null, new ActorStartOptions().waitForFinish(20L)).join();

    assertTrue(backend.lastUrl.contains("waitForFinish=20"), backend.lastUrl);
  }

  @Test
  void aTimedOutStartIsRetriedLikeAnyOtherRequest() {
    MockTransport backend =
        new MockTransport(List.of(MockTransport.timeoutError(), MockTransport.ok(201, RUN_JSON)));
    ApifyClient client = builder(backend).maxRetries(3).build();
    client.actor("a1").start(null, new ActorStartOptions()).join();
    assertEquals(2, backend.calls);
  }

  @Test
  void aRetryDoublesTheTimeoutUpToTheCap() {
    MockTransport backend =
        new MockTransport(
            List.of(
                MockTransport.ok(500, "{}"),
                MockTransport.ok(500, "{}"),
                MockTransport.ok(500, "{}"),
                MockTransport.ok(200, RUN_JSON)));
    ApifyClient client = builder(backend).maxRetries(5).timeoutMax(Duration.ofSeconds(15)).build();
    client.run("r1").get().join();
    assertEquals(List.of(secs(5), secs(10), secs(15), secs(15)), backend.timeouts);
  }

  @Test
  void aTimedOutPushIsNotRetriedSoItemsAreNotStoredTwice() {
    MockTransport backend =
        new MockTransport(List.of(MockTransport.timeoutError(), MockTransport.ok(201, "{}")));
    ApifyClient client = builder(backend).maxRetries(3).build();

    var future = client.dataset("d1").pushItems(java.util.Map.of("a", 1));
    var error = assertThrows(CompletionException.class, future::join);
    assertTrue(error.getCause() instanceof ApifyTransportException);
    assertEquals(1, backend.calls, "a push that timed out must not be sent again");
  }

  @Test
  void setRecordTakesATierAnExactDurationOrNoTimeout() {
    MockTransport backend = MockTransport.ofConstant(201, "");
    ApifyClient client = client(backend);
    byte[] value = {1};

    client.keyValueStore("s").setRecord("k", value, "text/plain").join();
    assertEquals(secs(360), backend.lastTimeout, "defaults to the long tier");

    client
        .keyValueStore("s")
        .setRecord("k", value, "text/plain", new SetRecordOptions().timeoutSecs(12L))
        .join();
    assertEquals(secs(12), backend.lastTimeout);

    client
        .keyValueStore("s")
        .setRecord("k", value, "text/plain", new SetRecordOptions().timeout(Timeout.SHORT))
        .join();
    assertEquals(secs(5), backend.lastTimeout);

    client
        .keyValueStore("s")
        .setRecord("k", value, "text/plain", new SetRecordOptions().timeout(Timeout.NO_TIMEOUT))
        .join();
    assertEquals(Optional.empty(), backend.lastTimeout);
  }

  @Test
  void timeoutsAreValidated() {
    assertThrows(IllegalArgumentException.class, () -> Timeout.of(Duration.ZERO));
    assertThrows(IllegalArgumentException.class, () -> Timeout.ofSeconds(-1));
    assertThrows(IllegalArgumentException.class, () -> Timeout.of(null));
    assertThrows(
        IllegalArgumentException.class, () -> ApifyClient.builder().timeoutShort(Duration.ZERO));
    assertThrows(IllegalArgumentException.class, () -> ApifyClient.builder().timeoutMedium(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> ApifyClient.builder().timeoutLong(Duration.ofSeconds(-1)));
    assertThrows(
        IllegalArgumentException.class, () -> ApifyClient.builder().timeoutMax(Duration.ZERO));
    assertThrows(IllegalArgumentException.class, () -> ApifyClient.create("t").withTimeout(null));
  }

  @Test
  void timeoutValuesDescribeThemselves() {
    assertEquals(Timeout.ofSeconds(3), Timeout.of(Duration.ofSeconds(3)));
    assertFalse(Timeout.SHORT.equals(Timeout.MEDIUM));
    assertEquals(Optional.of(Timeout.Tier.LONG), Timeout.LONG.tier());
    assertTrue(Timeout.NO_TIMEOUT.isNoTimeout());
    assertEquals(Optional.of(Duration.ofSeconds(3)), Timeout.ofSeconds(3).duration());
    assertEquals("Timeout.NO_TIMEOUT", Timeout.NO_TIMEOUT.toString());
  }
}
