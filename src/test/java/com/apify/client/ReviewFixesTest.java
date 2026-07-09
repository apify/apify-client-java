package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Offline tests pinning correctness behaviours surfaced in review (idempotency, chunking, ...). */
class ReviewFixesTest {

  private static ApifyClient client(MockBackend backend) {
    return client(backend, 0);
  }

  private static ApifyClient client(MockBackend backend, int maxRetries) {
    return ApifyClient.builder()
        .token("test-token")
        .httpBackend(backend)
        .maxRetries(maxRetries)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  @Test
  void chargeSendsIdempotencyKey() {
    MockBackend backend = MockBackend.ofConstant(200, "{\"data\":{}}");
    client(backend).run("run123").charge(new RunChargeOptions("my-event"));
    String key = backend.lastHeaders.firstValue("idempotency-key").orElse("");
    assertTrue(
        key.startsWith("run123-my-event-"), "idempotency key should embed run id + event: " + key);
    assertTrue(backend.lastBody.contains("\"eventName\":\"my-event\""), backend.lastBody);
  }

  @Test
  void lastRunForwardsStatusFilterToNestedStorages() {
    // A status/origin-filtered last-run client must forward those filters to its nested
    // dataset/key-value-store/request-queue/log accessors, so they resolve the same run.
    MockBackend ds = MockBackend.ofConstant(200, "[]");
    client(ds)
        .actor("me/x")
        .lastRun(RunStatus.SUCCEEDED)
        .dataset()
        .listItems(new DatasetListItemsOptions());
    assertTrue(ds.lastUrl.contains("runs/last/dataset/items"), ds.lastUrl);
    assertTrue(ds.lastUrl.contains("status=SUCCEEDED"), ds.lastUrl);

    MockBackend log = MockBackend.ofConstant(200, "log-text");
    client(log)
        .actor("me/x")
        .lastRun(new LastRunOptions().status(RunStatus.SUCCEEDED).origin(RunOrigin.API))
        .log()
        .get();
    assertTrue(log.lastUrl.contains("runs/last/log"), log.lastUrl);
    assertTrue(log.lastUrl.contains("status=SUCCEEDED"), log.lastUrl);
    assertTrue(log.lastUrl.contains("origin=API"), log.lastUrl);
  }

  @Test
  void plainRunNestedStoragesCarryNoInheritedFilter() {
    // A non-last-run client has no pinned params, so nested accessors stay filter-free.
    MockBackend ds = MockBackend.ofConstant(200, "[]");
    client(ds).run("run123").dataset().listItems(new DatasetListItemsOptions());
    assertTrue(ds.lastUrl.contains("actor-runs/run123/dataset/items"), ds.lastUrl);
    assertFalse(ds.lastUrl.contains("status="), ds.lastUrl);
  }

  @Test
  void chargeHonorsExplicitIdempotencyKey() {
    MockBackend backend = MockBackend.ofConstant(200, "{\"data\":{}}");
    client(backend).run("run123").charge(new RunChargeOptions("e").idempotencyKey("fixed-key"));
    assertEquals("fixed-key", backend.lastHeaders.firstValue("idempotency-key").orElse(""));
  }

  @Test
  void chargeRejectsMissingEventName() {
    MockBackend backend = MockBackend.ofConstant(200, "{\"data\":{}}");
    ApifyClient client = client(backend);
    assertThrows(
        IllegalArgumentException.class, () -> client.run("r").charge(new RunChargeOptions(null)));
    assertThrows(
        IllegalArgumentException.class, () -> client.run("r").charge(new RunChargeOptions("")));
    assertEquals(0, backend.calls, "no request should be sent for an invalid charge");
  }

  @Test
  void getRecordDefaultsAttachment() {
    MockBackend backend = MockBackend.ofConstant(200, "raw-bytes");
    client(backend).keyValueStore("store1").getRecord("OUTPUT");
    assertTrue(backend.lastUrl.contains("attachment=1"), backend.lastUrl);
  }

  @Test
  void keyValueStoreRecordDefensivelyCopiesBytes() {
    MockBackend backend = MockBackend.ofConstant(200, "raw-bytes");
    KeyValueStoreRecord record =
        client(backend).keyValueStore("s").getRecord("OUTPUT").orElseThrow();
    byte[] first = record.getValue();
    first[0] = 0; // mutate the returned array
    assertEquals('r', record.getValue()[0], "mutating a returned copy must not affect the record");
    assertEquals(
        "raw-bytes", new String(record.getValue(), java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  void setRecordDoesNotRetryTimeoutsWhenOptedOut() {
    MockBackend timeouts = new MockBackend(List.of(MockBackend.timeoutError()));
    assertThrows(
        HttpClientCore.TransportException.class,
        () ->
            client(timeouts, 3)
                .keyValueStore("s")
                .setRecord(
                    "k",
                    new byte[] {1},
                    "application/octet-stream",
                    new SetRecordOptions().doNotRetryTimeouts(true)));
    assertEquals(1, timeouts.calls, "a timeout must not be retried when doNotRetryTimeouts is set");
  }

  @Test
  void setRecordRetriesTimeoutsByDefault() {
    MockBackend timeouts = new MockBackend(List.of(MockBackend.timeoutError()));
    assertThrows(
        HttpClientCore.TransportException.class,
        () -> client(timeouts, 3).keyValueStore("s").setRecord("k", new byte[] {1}, "text/plain"));
    assertEquals(4, timeouts.calls, "timeouts should be retried (maxRetries + 1 attempts)");
  }

  @Test
  void createKeysPublicUrlForwardsFilterOptions() {
    MockBackend backend = MockBackend.ofConstant(200, "{\"data\":{\"id\":\"store1\"}}");
    String url =
        client(backend)
            .keyValueStore("store1")
            .createKeysPublicUrl(new ListKeysOptions().prefix("img-").limit(10L), null);
    assertTrue(url.contains("prefix=img-"), url);
    assertTrue(url.contains("limit=10"), url);
    assertFalse(url.contains("signature="), "a public store should get no signature: " + url);
  }

  @Test
  void downloadItemsForwardsItemSelectionParams() {
    MockBackend backend = MockBackend.ofConstant(200, "col1,col2\n");
    client(backend)
        .dataset("d1")
        .downloadItems(
            DownloadItemsFormat.CSV,
            new DatasetDownloadOptions()
                .items(new DatasetListItemsOptions().limit(5L).desc(true).fields(List.of("a", "b")))
                .bom(true));
    assertTrue(backend.lastUrl.contains("format=csv"), backend.lastUrl);
    assertTrue(backend.lastUrl.contains("limit=5"), backend.lastUrl);
    assertTrue(backend.lastUrl.contains("desc=1"), backend.lastUrl);
    assertTrue(backend.lastUrl.contains("bom=1"), backend.lastUrl);
    assertTrue(backend.lastUrl.contains("fields=a%2Cb"), backend.lastUrl);
  }

  @Test
  void batchAddRequestsChunks() {
    MockBackend backend =
        MockBackend.ofConstant(
            200, "{\"data\":{\"processedRequests\":[],\"unprocessedRequests\":[]}}");
    List<RequestQueueRequest> requests = new ArrayList<>();
    for (int i = 0; i < 60; i++) {
      requests.add(new RequestQueueRequest("https://example.com", "k" + i));
    }
    client(backend)
        .requestQueue("q1")
        .batchAddRequests(
            requests,
            false,
            new BatchAddRequestsOptions().maxParallel(1).maxUnprocessedRequestsRetries(0));
    assertEquals(3, backend.calls); // 25 + 25 + 10
  }

  @Test
  void batchAddRequestsRetriesUnprocessed() {
    MockBackend backend =
        new MockBackend(
            List.of(
                MockBackend.ok(
                    200,
                    "{\"data\":{\"processedRequests\":[{\"uniqueKey\":\"k0\",\"requestId\":\"r0\"}],"
                        + "\"unprocessedRequests\":[{\"uniqueKey\":\"k1\",\"url\":\"https://example.com\"}]}}"),
                MockBackend.ok(
                    200,
                    "{\"data\":{\"processedRequests\":[{\"uniqueKey\":\"k1\",\"requestId\":\"r1\"}],"
                        + "\"unprocessedRequests\":[]}}")));
    List<RequestQueueRequest> requests =
        List.of(
            new RequestQueueRequest("https://example.com", "k0"),
            new RequestQueueRequest("https://example.com", "k1"));
    BatchAddResult result =
        client(backend)
            .requestQueue("q1")
            .batchAddRequests(
                requests,
                false,
                new BatchAddRequestsOptions().minDelayBetweenUnprocessedRequestsRetriesMillis(1L));
    assertEquals(2, backend.calls, "the unprocessed request should trigger one retry call");
    assertEquals(2, result.getProcessedRequests().size());
    assertTrue(result.getUnprocessedRequests().isEmpty(), "all requests should end up processed");
  }

  @Test
  void getWithWaitForwardsWaitForFinish() {
    MockBackend backend =
        MockBackend.ofConstant(200, "{\"data\":{\"id\":\"r1\",\"status\":\"RUNNING\"}}");
    Optional<ActorRun> run = client(backend).run("r1").getWithWait(30L);
    assertTrue(run.isPresent(), "run should be present");
    assertEquals("r1", run.get().getId());
    assertTrue(backend.lastUrl.contains("waitForFinish=30"), backend.lastUrl);
  }

  @Test
  void getWithWaitClampsServerWaitToConfiguredTimeout() {
    // With a 10s per-request timeout, a caller asking for waitForFinish=60 must be clamped below
    // the
    // timeout (10 - 5s margin = 5) so the synchronous get can't abort itself on the socket timeout.
    MockBackend backend =
        MockBackend.ofConstant(200, "{\"data\":{\"id\":\"r1\",\"status\":\"RUNNING\"}}");
    ApifyClient client =
        ApifyClient.builder()
            .token("t")
            .httpBackend(backend)
            .maxRetries(0)
            .timeout(Duration.ofSeconds(10))
            .build();
    client.run("r1").getWithWait(60L);
    assertTrue(backend.lastUrl.contains("waitForFinish=5"), backend.lastUrl);
    assertFalse(backend.lastUrl.contains("waitForFinish=60"), backend.lastUrl);
  }

  @Test
  void waitForFinishClampsServerWaitToConfiguredTimeout() {
    MockBackend backend =
        MockBackend.ofConstant(200, "{\"data\":{\"id\":\"r1\",\"status\":\"SUCCEEDED\"}}");
    ApifyClient client =
        ApifyClient.builder()
            .token("t")
            .httpBackend(backend)
            .maxRetries(0)
            .minDelayBetweenRetries(Duration.ofMillis(1))
            .timeout(Duration.ofSeconds(20))
            .build();
    client.run("r1").waitForFinish(120L);
    // 20s timeout - 5s margin = 15s server wait cap (below the 60s API cap and the 120s budget).
    assertTrue(backend.lastUrl.contains("waitForFinish=15"), backend.lastUrl);
  }

  @Test
  void getResourceReturnsEmptyOnNullData() {
    MockBackend backend = MockBackend.ofConstant(200, "{\"data\":null}");
    Optional<KeyValueStore> store = client(backend).keyValueStore("s").get();
    assertTrue(store.isEmpty(), "a 200 with null data must map to an empty Optional, not throw");
  }

  @Test
  void storeIterateDoesNotMutateCallerOptionsAndHonorsOffset() {
    MockBackend backend =
        MockBackend.ofConstant(
            200, "{\"data\":{\"items\":[],\"total\":0,\"offset\":0,\"limit\":0,\"count\":0}}");
    StoreListOptions options = new StoreListOptions().offset(100L).limit(50L);
    client(backend).store().iterate(options).findFirst();
    // The caller's initial offset must be honored for paging and left untouched afterwards.
    assertTrue(backend.lastUrl.contains("offset=100"), backend.lastUrl);
    assertEquals(100L, options.offsetValue(), "iteration must not mutate the caller's options");
  }

  @Test
  void storeIterateWalksMultiplePages() {
    MockBackend backend =
        new MockBackend(
            List.of(
                MockBackend.ok(
                    200,
                    "{\"data\":{\"items\":[{},{}],\"total\":3,\"offset\":0,\"limit\":2,\"count\":2}}"),
                MockBackend.ok(
                    200,
                    "{\"data\":{\"items\":[{}],\"total\":3,\"offset\":2,\"limit\":2,\"count\":1}}")));
    long count = client(backend).store().iterate(new StoreListOptions().limit(2L)).count();
    assertEquals(3, count, "iteration should walk across both pages");
    assertEquals(2, backend.calls, "one API call per page");
  }

  @Test
  void runCollectionListToleratesNullOptionsAndFilter() {
    MockBackend backend =
        MockBackend.ofConstant(
            200, "{\"data\":{\"items\":[],\"total\":0,\"offset\":0,\"limit\":0,\"count\":0}}");
    PaginationList<ActorRun> runs = client(backend).runs().list(null, null);
    assertEquals(0, runs.getItems().size());
  }

  @Test
  void nestedWebhookCollectionListsWithoutCreate() {
    MockBackend backend =
        MockBackend.ofConstant(
            200, "{\"data\":{\"items\":[],\"total\":0,\"offset\":0,\"limit\":0,\"count\":0}}");
    // Compile-time guarantee: the nested collection type has no create(); it only lists.
    NestedWebhookCollectionClient nested = client(backend).task("t1").webhooks();
    assertEquals(0, nested.list(new ListOptions()).getItems().size());
  }

  @Test
  void accountWebhookCollectionCanCreate() {
    MockBackend backend = MockBackend.ofConstant(200, "{\"data\":{\"id\":\"wh1\"}}");
    Webhook created = client(backend).webhooks().create(java.util.Map.of("eventTypes", List.of()));
    assertEquals("wh1", created.getId());
    assertTrue(backend.lastUrl.endsWith("/webhooks"), backend.lastUrl);
  }

  @Test
  void updateLimitsSendsPutToMeLimits() {
    MockBackend backend = MockBackend.ofConstant(200, "{}");
    client(backend).me().updateLimits(java.util.Map.of("maxMonthlyUsageUsd", 100));
    assertTrue(backend.lastUrl.endsWith("/users/me/limits"), backend.lastUrl);
    assertTrue(backend.lastBody.contains("\"maxMonthlyUsageUsd\":100"), backend.lastBody);
    assertEquals(1, backend.calls);
  }

  @Test
  void batchAddRequestsThrowsOnNonRetryableClientError() {
    // A hard 4xx (e.g. bad token / insufficient permissions) must surface, not be masked as
    // "unprocessed" — otherwise a caller cannot tell it apart from ordinary rate-limiting.
    MockBackend backend =
        MockBackend.ofConstant(
            403, "{\"error\":{\"type\":\"insufficient-permissions\",\"message\":\"no\"}}");
    ApifyApiException ex =
        assertThrows(
            ApifyApiException.class,
            () ->
                client(backend)
                    .requestQueue("q1")
                    .batchAddRequests(
                        List.of(new RequestQueueRequest("https://example.com", "k0")),
                        false,
                        new BatchAddRequestsOptions().maxUnprocessedRequestsRetries(0)));
    assertEquals(403, ex.getStatusCode());
  }

  @Test
  void batchAddRequestsRunsChunksInParallel() {
    MockBackend backend =
        MockBackend.ofConstant(
            200, "{\"data\":{\"processedRequests\":[],\"unprocessedRequests\":[]}}");
    List<RequestQueueRequest> requests = new ArrayList<>();
    for (int i = 0; i < 60; i++) {
      requests.add(new RequestQueueRequest("https://example.com", "k" + i));
    }
    client(backend)
        .requestQueue("q1")
        .batchAddRequests(
            requests,
            false,
            new BatchAddRequestsOptions().maxParallel(3).maxUnprocessedRequestsRetries(0));
    assertEquals(3, backend.calls, "60 requests must be sent as 3 parallel chunks of 25/25/10");
  }

  @Test
  void waitForFinishReturnsWhenResourceAppearsAfter404() {
    // A just-started run can transiently 404 (replica lag); the wait must keep polling until it
    // appears and reaches a terminal state rather than giving up on the first 404.
    MockBackend backend =
        new MockBackend(
            List.of(
                MockBackend.ok(
                    404, "{\"error\":{\"type\":\"record-not-found\",\"message\":\"missing\"}}"),
                MockBackend.ok(200, "{\"data\":{\"id\":\"r1\",\"status\":\"SUCCEEDED\"}}")));
    ActorRun run = client(backend).run("r1").waitForFinish(5L);
    assertEquals("r1", run.getId());
    assertEquals(RunStatus.SUCCEEDED, run.getStatus());
    assertEquals(2, backend.calls, "one 404 poll then the terminal poll");
  }

  @Test
  void waitForFinishThrowsWhenResourceNeverAppears() {
    // If the resource is never fetchable within the budget, the wait must fail loudly rather than
    // return a phantom result.
    MockBackend backend =
        MockBackend.ofConstant(
            404, "{\"error\":{\"type\":\"record-not-found\",\"message\":\"missing\"}}");
    assertThrows(IllegalStateException.class, () -> client(backend).run("r1").waitForFinish(0L));
  }

  @Test
  void logStreamThrowsOnNonSuccessStatus() {
    MockBackend backend = MockBackend.ofConstant(200, "");
    backend.scriptStream(
        403, "{\"error\":{\"type\":\"insufficient-permissions\",\"message\":\"no\"}}");
    ApifyApiException ex =
        assertThrows(ApifyApiException.class, () -> client(backend).log("run1").stream());
    assertEquals(403, ex.getStatusCode());
  }
}
