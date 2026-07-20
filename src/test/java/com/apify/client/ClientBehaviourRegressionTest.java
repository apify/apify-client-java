package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.actor.Actor;
import com.apify.client.actor.ActorListOptions;
import com.apify.client.actor.ActorVersion;
import com.apify.client.dataset.DatasetDownloadOptions;
import com.apify.client.dataset.DatasetListItemsOptions;
import com.apify.client.dataset.DownloadItemsFormat;
import com.apify.client.http.ApifyApiException;
import com.apify.client.http.ApifyTransportException;
import com.apify.client.keyvalue.KeyValueStore;
import com.apify.client.keyvalue.KeyValueStoreRecord;
import com.apify.client.keyvalue.ListKeysOptions;
import com.apify.client.keyvalue.SetRecordOptions;
import com.apify.client.requestqueue.BatchAddRequestsOptions;
import com.apify.client.requestqueue.BatchAddResult;
import com.apify.client.requestqueue.RequestQueueRequest;
import com.apify.client.run.ActorRun;
import com.apify.client.run.LastRunOptions;
import com.apify.client.run.RunChargeOptions;
import com.apify.client.store.ActorStoreListItem;
import com.apify.client.store.StoreListOptions;
import com.apify.client.webhook.NestedWebhookCollectionClient;
import com.apify.client.webhook.Webhook;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Offline regression tests pinning client behaviours: idempotency-key derivation, filter
 * propagation through nested clients, retry/timeout policy, request chunking, pagination/iteration
 * termination, and wait/poll semantics.
 */
class ClientBehaviourRegressionTest {

  private static ApifyClient client(MockTransport backend) {
    return client(backend, 0);
  }

  private static ApifyClient client(MockTransport backend, int maxRetries) {
    return ApifyClient.builder()
        .token("test-token")
        .httpTransport(backend)
        .maxRetries(maxRetries)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  @Test
  void chargeSendsIdempotencyKey() {
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":{}}");
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
    MockTransport ds = MockTransport.ofConstant(200, "[]");
    client(ds)
        .actor("me/x")
        .lastRun("SUCCEEDED")
        .dataset()
        .listItems(new DatasetListItemsOptions());
    assertTrue(ds.lastUrl.contains("runs/last/dataset/items"), ds.lastUrl);
    assertTrue(ds.lastUrl.contains("status=SUCCEEDED"), ds.lastUrl);

    MockTransport log = MockTransport.ofConstant(200, "log-text");
    client(log)
        .actor("me/x")
        .lastRun(new LastRunOptions().status("SUCCEEDED").origin("API"))
        .log()
        .get();
    assertTrue(log.lastUrl.contains("runs/last/log"), log.lastUrl);
    assertTrue(log.lastUrl.contains("status=SUCCEEDED"), log.lastUrl);
    assertTrue(log.lastUrl.contains("origin=API"), log.lastUrl);
  }

  @Test
  void plainRunNestedStoragesCarryNoInheritedFilter() {
    // A non-last-run client has no pinned params, so nested accessors stay filter-free.
    MockTransport ds = MockTransport.ofConstant(200, "[]");
    client(ds).run("run123").dataset().listItems(new DatasetListItemsOptions());
    assertTrue(ds.lastUrl.contains("actor-runs/run123/dataset/items"), ds.lastUrl);
    assertFalse(ds.lastUrl.contains("status="), ds.lastUrl);
  }

  @Test
  void chargeHonorsExplicitIdempotencyKey() {
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":{}}");
    client(backend).run("run123").charge(new RunChargeOptions("e").idempotencyKey("fixed-key"));
    assertEquals("fixed-key", backend.lastHeaders.firstValue("idempotency-key").orElse(""));
  }

  @Test
  void chargeRejectsMissingEventName() {
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":{}}");
    ApifyClient client = client(backend);
    assertThrows(
        IllegalArgumentException.class, () -> client.run("r").charge(new RunChargeOptions(null)));
    assertThrows(
        IllegalArgumentException.class, () -> client.run("r").charge(new RunChargeOptions("")));
    assertEquals(0, backend.calls, "no request should be sent for an invalid charge");
  }

  @Test
  void getRecordDefaultsAttachment() {
    MockTransport backend = MockTransport.ofConstant(200, "raw-bytes");
    client(backend).keyValueStore("store1").getRecord("OUTPUT");
    assertTrue(backend.lastUrl.contains("attachment=1"), backend.lastUrl);
  }

  @Test
  void keyValueStoreRecordDefensivelyCopiesBytes() {
    MockTransport backend = MockTransport.ofConstant(200, "raw-bytes");
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
    MockTransport timeouts = new MockTransport(List.of(MockTransport.timeoutError()));
    assertThrows(
        ApifyTransportException.class,
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
    MockTransport timeouts = new MockTransport(List.of(MockTransport.timeoutError()));
    assertThrows(
        ApifyTransportException.class,
        () -> client(timeouts, 3).keyValueStore("s").setRecord("k", new byte[] {1}, "text/plain"));
    assertEquals(4, timeouts.calls, "timeouts should be retried (maxRetries + 1 attempts)");
  }

  @Test
  void createKeysPublicUrlForwardsFilterOptions() {
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":{\"id\":\"store1\"}}");
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
    MockTransport backend = MockTransport.ofConstant(200, "col1,col2\n");
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
    MockTransport backend =
        MockTransport.ofConstant(
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
    MockTransport backend =
        new MockTransport(
            List.of(
                MockTransport.ok(
                    200,
                    "{\"data\":{\"processedRequests\":[{\"uniqueKey\":\"k0\",\"requestId\":\"r0\"}],"
                        + "\"unprocessedRequests\":[{\"uniqueKey\":\"k1\",\"url\":\"https://example.com\"}]}}"),
                MockTransport.ok(
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
    MockTransport backend =
        MockTransport.ofConstant(200, "{\"data\":{\"id\":\"r1\",\"status\":\"RUNNING\"}}");
    Optional<ActorRun> run = client(backend).run("r1").getWithWait(30L);
    assertTrue(run.isPresent(), "run should be present");
    assertEquals("r1", run.get().getId());
    assertTrue(backend.lastUrl.contains("waitForFinish=30"), backend.lastUrl);
  }

  @Test
  void getWithWaitClampsServerWaitToConfiguredTimeout() {
    // With a 10s per-request timeout, a caller asking for waitForFinish=60 must be clamped below
    // the timeout (10 - 5s margin = 5) so the synchronous get can't abort itself on the socket
    // timeout.
    MockTransport backend =
        MockTransport.ofConstant(200, "{\"data\":{\"id\":\"r1\",\"status\":\"RUNNING\"}}");
    ApifyClient client =
        ApifyClient.builder()
            .token("t")
            .httpTransport(backend)
            .maxRetries(0)
            .timeout(Duration.ofSeconds(10))
            .build();
    client.run("r1").getWithWait(60L);
    assertTrue(backend.lastUrl.contains("waitForFinish=5"), backend.lastUrl);
    assertFalse(backend.lastUrl.contains("waitForFinish=60"), backend.lastUrl);
  }

  @Test
  void waitForFinishClampsServerWaitToConfiguredTimeout() {
    MockTransport backend =
        MockTransport.ofConstant(200, "{\"data\":{\"id\":\"r1\",\"status\":\"SUCCEEDED\"}}");
    ApifyClient client =
        ApifyClient.builder()
            .token("t")
            .httpTransport(backend)
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
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":null}");
    Optional<KeyValueStore> store = client(backend).keyValueStore("s").get();
    assertTrue(store.isEmpty(), "a 200 with null data must map to an empty Optional, not throw");
  }

  @Test
  void storeIterateDoesNotMutateCallerOptionsAndHonorsOffset() {
    MockTransport backend =
        MockTransport.ofConstant(
            200, "{\"data\":{\"items\":[],\"total\":0,\"offset\":0,\"limit\":0,\"count\":0}}");
    StoreListOptions options = new StoreListOptions().offset(100L).limit(50L);
    client(backend).store().iterate(options, null).hasNext();
    // The caller's initial offset must be honored for paging and left untouched afterwards.
    assertTrue(backend.lastUrl.contains("offset=100"), backend.lastUrl);
    assertEquals(100L, options.offsetValue(), "iteration must not mutate the caller's options");
  }

  @Test
  void storeIterateWalksMultiplePages() {
    MockTransport backend =
        new MockTransport(
            List.of(
                MockTransport.ok(
                    200,
                    "{\"data\":{\"items\":[{},{}],\"total\":3,\"offset\":0,\"limit\":2,\"count\":2}}"),
                MockTransport.ok(
                    200,
                    "{\"data\":{\"items\":[{}],\"total\":3,\"offset\":2,\"limit\":2,\"count\":1}}"),
                // Trailing empty page: the iterator stops on an empty page (it does not trust the
                // reported total, which some endpoints under-report), so a final empty page is
                // required to terminate an uncapped walk.
                MockTransport.ok(
                    200,
                    "{\"data\":{\"items\":[],\"total\":3,\"offset\":3,\"limit\":2,\"count\":0}}")));
    // No total cap; page size 2 drives paging until the empty page.
    java.util.Iterator<ActorStoreListItem> it =
        client(backend).store().iterate(new StoreListOptions(), 2L);
    int count = 0;
    while (it.hasNext()) {
      it.next();
      count++;
    }
    assertEquals(3, count, "iteration should walk across both non-empty pages");
    assertEquals(3, backend.calls, "two data pages plus the terminating empty page");
  }

  @Test
  void collectionIterateSingleArgDelegatesToServerDefaultPageSize() {
    // The arg-less-chunkSize convenience overload must page correctly (delegates with null chunk).
    MockTransport backend =
        new MockTransport(
            List.of(
                MockTransport.ok(
                    200,
                    "{\"data\":{\"items\":[{},{}],\"total\":2,\"offset\":0,\"limit\":2,\"count\":2}}"),
                MockTransport.ok(
                    200,
                    "{\"data\":{\"items\":[],\"total\":2,\"offset\":2,\"limit\":2,\"count\":0}}")));
    java.util.Iterator<Actor> it = client(backend).actors().iterate(new ActorListOptions());
    int count = 0;
    while (it.hasNext()) {
      it.next();
      count++;
    }
    assertEquals(2, count, "single-arg iterate should yield every item");
    assertFalse(
        backend.lastUrl.contains("limit="), "no chunkSize => no limit param (server default)");
  }

  @Test
  void iterateSnapshotsOptionsSoLaterMutationsDoNotLeak() {
    // The iterator must capture the options (offset/limit AND filters) at call time; mutating the
    // caller's options object afterwards must not change subsequent page requests.
    MockTransport backend =
        MockTransport.ofConstant(
            200, "{\"data\":{\"items\":[{}],\"total\":1,\"offset\":0,\"limit\":0,\"count\":1}}");
    ActorListOptions options = new ActorListOptions().sortBy("createdAt");
    java.util.Iterator<Actor> it = client(backend).actors().iterate(options);
    options.sortBy("modifiedAt"); // mutate after obtaining the iterator
    it.hasNext(); // triggers the first page fetch
    assertTrue(backend.lastUrl.contains("sortBy=createdAt"), backend.lastUrl);
    assertFalse(backend.lastUrl.contains("modifiedAt"), backend.lastUrl);
  }

  @Test
  void runCollectionListToleratesNullOptionsAndFilter() {
    MockTransport backend =
        MockTransport.ofConstant(
            200, "{\"data\":{\"items\":[],\"total\":0,\"offset\":0,\"limit\":0,\"count\":0}}");
    PaginationList<ActorRun> runs = client(backend).runs().list(null, null);
    assertEquals(0, runs.getItems().size());
  }

  @Test
  void nestedWebhookCollectionListsWithoutCreate() {
    MockTransport backend =
        MockTransport.ofConstant(
            200, "{\"data\":{\"items\":[],\"total\":0,\"offset\":0,\"limit\":0,\"count\":0}}");
    // Compile-time guarantee: the nested collection type has no create(); it only lists.
    NestedWebhookCollectionClient nested = client(backend).task("t1").webhooks();
    assertEquals(0, nested.list(new ListOptions()).getItems().size());
  }

  @Test
  void accountWebhookCollectionCanCreate() {
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":{\"id\":\"wh1\"}}");
    Webhook created = client(backend).webhooks().create(java.util.Map.of("eventTypes", List.of()));
    assertEquals("wh1", created.getId());
    assertTrue(backend.lastUrl.endsWith("/webhooks"), backend.lastUrl);
  }

  @Test
  void updateLimitsSendsPutToMeLimits() {
    MockTransport backend = MockTransport.ofConstant(200, "{}");
    client(backend).me().updateLimits(java.util.Map.of("maxMonthlyUsageUsd", 100));
    assertTrue(backend.lastUrl.endsWith("/users/me/limits"), backend.lastUrl);
    assertTrue(backend.lastBody.contains("\"maxMonthlyUsageUsd\":100"), backend.lastBody);
    assertEquals(1, backend.calls);
  }

  @Test
  void batchAddRequestsThrowsOnNonRetryableClientError() {
    // A hard 4xx (e.g. bad token / insufficient permissions) must surface, not be masked as
    // "unprocessed" — otherwise a caller cannot tell it apart from ordinary rate-limiting.
    MockTransport backend =
        MockTransport.ofConstant(
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
    MockTransport backend =
        MockTransport.ofConstant(
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
    MockTransport backend =
        new MockTransport(
            List.of(
                MockTransport.ok(
                    404, "{\"error\":{\"type\":\"record-not-found\",\"message\":\"missing\"}}"),
                MockTransport.ok(200, "{\"data\":{\"id\":\"r1\",\"status\":\"SUCCEEDED\"}}")));
    ActorRun run = client(backend).run("r1").waitForFinish(5L);
    assertEquals("r1", run.getId());
    assertEquals("SUCCEEDED", run.getStatus());
    assertEquals(2, backend.calls, "one 404 poll then the terminal poll");
  }

  @Test
  void waitForFinishThrowsWhenResourceNeverAppears() {
    // If the resource is never fetchable within the budget, the wait must fail loudly rather than
    // return a phantom result.
    MockTransport backend =
        MockTransport.ofConstant(
            404, "{\"error\":{\"type\":\"record-not-found\",\"message\":\"missing\"}}");
    assertThrows(IllegalStateException.class, () -> client(backend).run("r1").waitForFinish(0L));
  }

  @Test
  void logStreamThrowsOnNonSuccessStatus() {
    MockTransport backend = MockTransport.ofConstant(200, "");
    backend.scriptStream(
        403, "{\"error\":{\"type\":\"insufficient-permissions\",\"message\":\"no\"}}");
    ApifyApiException ex =
        assertThrows(ApifyApiException.class, () -> client(backend).log("run1").stream());
    assertEquals(403, ex.getStatusCode());
  }

  @Test
  void versionsIterateSingleFetchTerminatesAndDoesNotDuplicate() {
    // GET /v2/actors/{actorId}/versions is NOT offset/limit paginated: the server ignores `offset`
    // and returns the full {total, items} list on every request. `ofConstant` reproduces exactly
    // that (same non-empty page for every call). Draining the iterator must terminate and yield
    // each version once. Routing this endpoint through the offset/limit paging engine looped
    // forever (empty-page termination never triggers), so this pins the single-fetch behaviour.
    MockTransport backend =
        MockTransport.ofConstant(
            200,
            "{\"data\":{\"total\":3,\"items\":["
                + "{\"versionNumber\":\"0.1\"},"
                + "{\"versionNumber\":\"0.2\"},"
                + "{\"versionNumber\":\"0.3\"}]}}");
    java.util.Iterator<ActorVersion> it =
        client(backend).actor("me/actor").versions().iterate(new ListOptions());
    List<String> yielded = new ArrayList<>();
    int guard = 0;
    while (it.hasNext()) {
      // Guard converts a termination regression (infinite loop) into a clean failure, not a hang.
      assertTrue(
          ++guard <= 100, "versions iterator did not terminate (paged a non-paginated endpoint)");
      yielded.add(it.next().getVersionNumber());
    }
    assertEquals(List.of("0.1", "0.2", "0.3"), yielded, "each version yielded once, in order");
    assertEquals(1, backend.calls, "non-paginated versions endpoint must be fetched exactly once");
  }

  @Test
  void versionsIterateHonorsTotalLimitCap() {
    MockTransport backend =
        MockTransport.ofConstant(
            200,
            "{\"data\":{\"total\":3,\"items\":["
                + "{\"versionNumber\":\"0.1\"},"
                + "{\"versionNumber\":\"0.2\"},"
                + "{\"versionNumber\":\"0.3\"}]}}");
    java.util.Iterator<ActorVersion> it =
        client(backend).actor("me/actor").versions().iterate(new ListOptions().limit(2L));
    List<String> yielded = new ArrayList<>();
    while (it.hasNext()) {
      yielded.add(it.next().getVersionNumber());
    }
    assertEquals(List.of("0.1", "0.2"), yielded, "limit caps the number yielded");
  }
}
