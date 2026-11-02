package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.actor.ActorStartOptions;
import com.apify.client.run.ActorRun;
import com.apify.client.run.RunListOptions;
import com.apify.client.store.StoreListOptions;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Offline tests for what the client sends: pushed dataset items, Actor inputs, default headers,
 * path-safe resource ids, and the query parameters added with the OpenAPI spec updates.
 */
class ClientInputTest {

  private static final String RUN_JSON = "{\"data\":{\"id\":\"r1\",\"status\":\"READY\"}}";

  private static ApifyClient client(MockTransport backend) {
    return ApifyClient.builder().token("t").httpTransport(backend).maxRetries(0).build();
  }

  // ---- pushItems accepts only what the API accepts --------------------------------------------

  @Test
  void pushItemsSendsAnObjectOrAnArrayOfObjects() {
    MockTransport backend = MockTransport.ofConstant(201, "{}");
    client(backend).dataset("d").pushItems(Map.of("a", 1)).join();
    assertEquals("{\"a\":1}", backend.lastBody);

    client(backend).dataset("d").pushItems(List.of(Map.of("a", 1), Map.of("b", 2))).join();
    assertEquals("[{\"a\":1},{\"b\":2}]", backend.lastBody);
  }

  @Test
  void pushItemsSendsAPreSerializedStringAsItIs() {
    MockTransport backend = MockTransport.ofConstant(201, "{}");
    client(backend).dataset("d").pushItems("[{\"a\": 1}, {\"b\": 2}]").join();
    assertEquals("[{\"a\": 1}, {\"b\": 2}]", backend.lastBody, "not wrapped in a JSON string");
  }

  @Test
  void pushItemsRejectsShapesTheApiWouldReject() {
    MockTransport backend = MockTransport.ofConstant(201, "{}");
    var dataset = client(backend).dataset("d");
    assertThrows(IllegalArgumentException.class, () -> dataset.pushItems(null));
    assertThrows(IllegalArgumentException.class, () -> dataset.pushItems(42));
    assertThrows(IllegalArgumentException.class, () -> dataset.pushItems(List.of("a", "b")));
    assertThrows(IllegalArgumentException.class, () -> dataset.pushItems(new String[] {"a"}));
    assertThrows(IllegalArgumentException.class, () -> dataset.pushItems("just text"));
    assertThrows(IllegalArgumentException.class, () -> dataset.pushItems("[1, 2]"));
    assertThrows(
        IllegalArgumentException.class, () -> dataset.pushItems(List.of(Map.of("a", 1), "b")));
    assertEquals(0, backend.calls, "nothing is sent for an invalid payload");
  }

  // ---- Actor input -----------------------------------------------------------------------------

  @Test
  void aByteArrayInputIsSentAsRawBytes() {
    MockTransport backend = MockTransport.ofConstant(201, RUN_JSON);
    byte[] input = "{\"x\": 1}".getBytes(StandardCharsets.UTF_8);
    client(backend).actor("a").start(input, new ActorStartOptions()).join();
    assertArrayEquals(input, backend.lastBodyBytes);
  }

  @Test
  void aStringInputIsSentAsItIsWithTheGivenContentType() {
    MockTransport backend = MockTransport.ofConstant(201, RUN_JSON);
    client(backend)
        .actor("a")
        .start("a,b\n1,2", new ActorStartOptions().contentType("text/csv"))
        .join();
    assertEquals("a,b\n1,2", backend.lastBody);
    assertEquals("text/csv", backend.lastHeaders.firstValue("Content-Type").orElse(null));
  }

  @Test
  void anObjectInputIsSerializedToJson() {
    MockTransport backend = MockTransport.ofConstant(201, RUN_JSON);
    client(backend).actor("a").start(Map.of("k", "v"), new ActorStartOptions()).join();
    assertEquals("{\"k\":\"v\"}", backend.lastBody);
  }

  // ---- default headers -------------------------------------------------------------------------

  @Test
  void defaultHeadersAreSentWithEveryRequestAndWinOverTheBuiltInOnes() {
    MockTransport backend = MockTransport.ofConstant(200, RUN_JSON);
    ApifyClient client =
        ApifyClient.builder()
            .token("t")
            .httpTransport(backend)
            .maxRetries(0)
            .header("X-Integration", "my-platform")
            .header("user-agent", "custom-agent/1")
            .build();
    client.run("r1").get().join();
    assertEquals("my-platform", backend.lastHeaders.firstValue("X-Integration").orElse(null));
    assertEquals(
        List.of("custom-agent/1"),
        backend.lastHeaders.allValues("User-Agent"),
        "replaced, not added");
    assertEquals("Bearer t", backend.lastHeaders.firstValue("Authorization").orElse(null));
  }

  @Test
  void aHeaderSetAgainReplacesTheEarlierOneCaseInsensitively() {
    MockTransport backend = MockTransport.ofConstant(200, RUN_JSON);
    ApifyClient.builder()
        .token("t")
        .httpTransport(backend)
        .header("X-A", "1")
        .header("x-a", "2")
        .build()
        .run("r1")
        .get()
        .join();
    assertEquals(List.of("2"), backend.lastHeaders.allValues("X-A"));
  }

  @Test
  void builderRejectsABlankHeaderNameAndANullValue() {
    assertThrows(IllegalArgumentException.class, () -> ApifyClient.builder().header(" ", "v"));
    assertThrows(IllegalArgumentException.class, () -> ApifyClient.builder().header("X", null));
  }

  // ---- path-safe resource ids ------------------------------------------------------------------

  @Test
  void resourceIdsAreEncodedIntoASinglePathSegment() {
    MockTransport backend = MockTransport.ofConstant(200, RUN_JSON);
    ApifyClient client = client(backend);
    client.run("r1?x=1#frag").get().join();
    assertTrue(backend.lastUrl.contains("/actor-runs/r1%3Fx%3D1%23frag"), backend.lastUrl);

    client.run("../../users/me").get().join();
    assertTrue(backend.lastUrl.contains("/actor-runs/..~..~users~me"), backend.lastUrl);

    client.actor("apify/hello-world").get().join();
    assertTrue(backend.lastUrl.contains("/actors/apify~hello-world"), backend.lastUrl);
  }

  @Test
  void anEmptyOrDotResourceIdIsRejectedInsteadOfAddressingTheCollection() {
    ApifyClient client = client(MockTransport.ofConstant(200, RUN_JSON));
    assertThrows(IllegalArgumentException.class, () -> client.run(""));
    assertThrows(IllegalArgumentException.class, () -> client.run(".."));
    assertThrows(IllegalArgumentException.class, () -> client.dataset("."));
  }

  // ---- users by id -----------------------------------------------------------------------------

  @Test
  void limitsAndMonthlyUsageAreAvailableForAnyUserId() {
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":{}}");
    client(backend).user("someone").limits().join();
    assertTrue(backend.lastUrl.endsWith("/users/someone/limits"), backend.lastUrl);

    client(backend).user("someone").monthlyUsage("2026-10-01").join();
    assertTrue(
        backend.lastUrl.contains("/users/someone/usage/monthly?date=2026-10-01"), backend.lastUrl);

    client(backend).user("someone").updateLimits(Map.of("maxMonthlyUsageUsd", 5)).join();
    assertEquals("PUT", backend.lastMethod);
    assertTrue(backend.lastUrl.endsWith("/users/someone/limits"), backend.lastUrl);
  }

  // ---- query parameters added with the spec ----------------------------------------------------

  @Test
  void runListsFilterByStandbyDeployment() {
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":{\"items\":[],\"total\":0}}");
    RunListOptions filter =
        new RunListOptions()
            .status(List.of("RUNNING"))
            .deployment(List.of("SINGLE_TENANT", "MULTI_TENANT_FREE_USERS"));
    client(backend).runs().list(new ListOptions(), filter).join();
    assertTrue(
        backend.lastUrl.contains("deployment=SINGLE_TENANT%2CMULTI_TENANT_FREE_USERS"),
        backend.lastUrl);
    client(backend).actor("a").runs().list(new ListOptions(), filter).join();
    assertTrue(backend.lastUrl.contains("deployment="), backend.lastUrl);
  }

  @Test
  void storeListCanIncludeTheInputSchema() {
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":{\"items\":[],\"total\":0}}");
    client(backend).store().list(new StoreListOptions().includeInputSchema(true)).join();
    assertTrue(backend.lastUrl.contains("includeInputSchema=1"), backend.lastUrl);
  }

  // ---- new response fields ---------------------------------------------------------------------

  @Test
  void runExposesTheServerActorDeploymentAndTheNewFields() {
    MockTransport backend =
        MockTransport.ofConstant(
            200,
            "{\"data\":{\"id\":\"r1\",\"standby\":{\"deployment\":\"MULTI_TENANT_PAYING_USERS\"},"
                + "\"consoleUrl\":\"https://console.apify.com/actors/runs/r1\","
                + "\"externalChargedTotalUsd\":0.5,"
                + "\"eventUsage\":{\"x\":{\"eventTotalUsd\":1.0,\"eventTitle\":\"X\"}},"
                + "\"meta\":{\"origin\":\"API\",\"actorRunId\":\"parent\"},"
                + "\"options\":{\"isMaxTotalChargeUsdSetByUser\":true}}}");
    ActorRun run = client(backend).run("r1").get().join().orElseThrow();
    assertEquals("MULTI_TENANT_PAYING_USERS", run.getStandby().getDeployment());
    assertEquals(0.5, run.getExternalChargedTotalUsd());
    assertEquals("parent", run.getMeta().getActorRunId());
    assertTrue(run.getOptions().getIsMaxTotalChargeUsdSetByUser());
    assertEquals(1.0, run.getEventUsage().get("x").get("eventTotalUsd").asDouble());
  }

  @Test
  void aRunWithoutStandbyHasNone() {
    MockTransport backend =
        MockTransport.ofConstant(200, "{\"data\":{\"id\":\"r1\",\"standby\":null}}");
    assertEquals(null, client(backend).run("r1").get().join().orElseThrow().getStandby());
  }

  @Test
  void scheduleToleratesANullUiState() {
    MockTransport backend =
        MockTransport.ofConstant(
            200, "{\"data\":{\"id\":\"s1\",\"cronExpression\":\"* * * * *\",\"uiState\":null}}");
    var schedule = client(backend).schedule("s1").get().join().orElseThrow();
    assertEquals(null, schedule.getUiState());

    backend =
        MockTransport.ofConstant(
            200, "{\"data\":{\"id\":\"s1\",\"uiState\":{\"mode\":\"advanced\"}}}");
    schedule = client(backend).schedule("s1").get().join().orElseThrow();
    assertEquals("advanced", schedule.getUiState().get("mode").asString());
  }

  @Test
  void aPendingRequestKeepsANullHandledAt() {
    MockTransport backend =
        MockTransport.ofConstant(
            200,
            "{\"data\":{\"id\":\"q1\",\"uniqueKey\":\"u\",\"url\":\"https://x.test\",\"handledAt\":null}}");
    var request = client(backend).requestQueue("q").getRequest("q1").join().orElseThrow();
    assertEquals(null, request.getHandledAt());
    assertFalse(backend.lastUrl.isEmpty());
  }

  @Test
  void webhookIntegrationFieldsAreTyped() {
    MockTransport backend =
        MockTransport.ofConstant(
            200,
            "{\"data\":{\"id\":\"w1\",\"isApifyIntegration\":true,\"isEnabled\":true,"
                + "\"actionType\":\"SLACK_MESSAGE\",\"title\":\"T\","
                + "\"slackMessage\":{\"channelId\":\"C1\"}}}");
    var webhook = client(backend).webhook("w1").get().join().orElseThrow();
    assertTrue(webhook.getIsApifyIntegration());
    assertEquals("SLACK_MESSAGE", webhook.getActionType());
    assertEquals("C1", webhook.getSlackMessage().get("channelId").asString());
  }

  @Test
  void clientIsBuildableWithTheLegacyAndNewTimeoutSettersTogether() {
    ApifyClient.builder()
        .token("t")
        .timeoutShort(Duration.ofSeconds(1))
        .timeoutMax(Duration.ofSeconds(30))
        .build();
  }
}
