package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.Publishers;
import com.apify.client.actor.ActorCallOptions;
import com.apify.client.actor.ActorClient;
import com.apify.client.actor.ActorStartOptions;
import com.apify.client.dataset.DatasetListItemsOptions;
import com.apify.client.http.Timeout;
import com.apify.client.keyvalue.ListKeysOptions;
import com.apify.client.log.StreamedLog;
import com.apify.client.log.StreamedLogOptions;
import com.apify.client.requestqueue.ListRequestsOptions;
import com.apify.client.run.ActorRun;
import com.apify.client.run.LastRunOptions;
import com.apify.client.run.RunClient;
import com.apify.client.run.RunListOptions;
import com.apify.client.run.RunResurrectOptions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ActorRunIntegrationTest extends IntegrationBase {

  // metamorph, reboot and charge have no live test: metamorph needs a second Actor with a
  // compatible input schema, reboot needs a run that is still running (hello-world finishes in a
  // couple of seconds), and charge needs a pay-per-event Actor. ClientBehaviourRegressionTest
  // covers them against a mock backend.

  @Test
  void listRuns() {
    ApifyClient client = requireClient();
    var page = client.runs().list(new ListOptions().limit(5L), new RunListOptions()).join();
    assertTrue(page.getTotal() >= 0);
  }

  @Test
  void listRunsFilteredByStandbyDeployment() {
    ApifyClient client = requireClient();
    // The deployment filter needs a status filter with only non-terminal statuses; whether any run
    // matches depends on the account, so the API accepting the combination is what is asserted.
    var filter =
        new RunListOptions()
            .status(List.of("READY", "RUNNING"))
            .deployment(List.of("SINGLE_TENANT", "MULTI_TENANT_FREE_USERS"));
    var page = client.runs().list(new ListOptions().limit(5L), filter).join();
    assertTrue(page.getTotal() >= 0);
    var actorPage =
        client.actor("apify/hello-world").runs().list(new ListOptions().limit(5L), filter).join();
    assertTrue(actorPage.getTotal() >= 0);
  }

  @Test
  void iterateRunDatasetItemsOfAFinishedRun() {
    ApifyClient client = requireClient();
    ActorRun run =
        client
            .actor("apify/hello-world")
            .call(null, new ActorStartOptions(), TEST_ACTOR_WAIT_SECS)
            .join();
    assertEquals("SUCCEEDED", run.getStatus());
    List<JsonNode> viaRun =
        Publishers.collect(client.run(run.getId()).iterateDatasetItems(null)).join();
    List<JsonNode> viaDataset =
        Publishers.collect(
                client
                    .dataset(run.getDefaultDatasetId())
                    .iterateItems(new DatasetListItemsOptions()))
            .join();
    assertEquals(viaDataset, viaRun, "the run iterator yields the run's dataset items");
  }

  @Test
  void clientWithATimeoutOverrideStillWorks() {
    ApifyClient client = requireClient();
    var page =
        client
            .withTimeout(Timeout.ofSeconds(60))
            .runs()
            .list(new ListOptions().limit(1L), new RunListOptions())
            .join();
    assertTrue(page.getTotal() >= 0);
  }

  @Test
  void runActorAndReadOutputs() {
    ApifyClient client = requireClient();
    ActorRun run =
        client
            .actor("apify/hello-world")
            .call(null, new ActorStartOptions(), TEST_ACTOR_WAIT_SECS)
            .join();
    assertEquals("SUCCEEDED", run.getStatus());

    assertTrue(client.run(run.getId()).get().join().isPresent());

    Optional<String> log = client.run(run.getId()).log().get().join();
    assertTrue(log.isPresent() && !log.get().isEmpty());

    Optional<byte[]> logBytes = client.run(run.getId()).log().getAsBytes().join();
    assertTrue(logBytes.isPresent() && logBytes.get().length > 0);

    client.run(run.getId()).dataset().listItems(new DatasetListItemsOptions()).join();
    client.run(run.getId()).keyValueStore().getRecord("OUTPUT").join();
    client.run(run.getId()).requestQueue().listHead(null).join();

    // Nested run-storage metadata GETs: the run's default storages are reachable as metadata
    // resources.
    assertTrue(client.run(run.getId()).dataset().get().join().isPresent());
    assertTrue(client.run(run.getId()).keyValueStore().get().join().isPresent());
    assertTrue(client.run(run.getId()).requestQueue().get().join().isPresent());

    // A few more run-scoped GETs not otherwise exercised on this particular (run-nested) storage
    // path; the same operations are already covered end-to-end against standalone storages
    // elsewhere (DatasetIntegrationTest/KeyValueStoreIntegrationTest/RequestQueueIntegrationTest),
    // this only confirms they also work when reached through `run().<storage>()`.
    client.run(run.getId()).dataset().getStatistics().join();
    client.run(run.getId()).keyValueStore().listKeys(new ListKeysOptions()).join();
    client.run(run.getId()).requestQueue().listRequests(new ListRequestsOptions()).join();
    // hello-world's default request queue is empty, so a made-up id is expected to resolve to
    // nothing; this still exercises the live GET-by-id code path through the run-nested client.
    assertTrue(
        client.run(run.getId()).requestQueue().getRequest("does-not-exist").join().isEmpty());

    // Typed getters: verify the API's response deserializes into them.
    assertTrue(run.getGeneralAccess() != null);
    assertTrue(run.getStats() != null);
    assertTrue(run.getOptions() != null && run.getOptions().getBuild() != null);
    assertTrue(run.getMeta() != null && run.getMeta().getOrigin() != null);
    assertTrue(run.getUsage() != null);
  }

  /**
   * Attempts and backoff bounding how long {@link #actorRunsNestedCollection} waits for a
   * just-started run to surface in the Actor's run collection listing. Same class of race as {@code
   * IterationIntegrationTest#ITER_FIND_ATTEMPTS}; kept as its own constant since this suite's retry
   * budget for a run to appear is independently tunable from that one's.
   */
  private static final int RUN_LIST_FIND_ATTEMPTS = 10;

  private static final long RUN_LIST_FIND_BACKOFF_MILLIS = 1000L;

  @Test
  void actorRunsNestedCollection() {
    ApifyClient client = requireClient();
    ActorClient actor = client.actor("apify/hello-world");
    ActorRun run = actor.call(null, new ActorStartOptions(), TEST_ACTOR_WAIT_SECS).join();

    // Runs are sorted ascending by startedAt by default, and "apify/hello-world" is a heavily
    // used public Actor, so the just-started run would never surface within a small default-order
    // page; request newest-first. The LIST endpoint's index can also lag a just-finished run by a
    // moment (eventual consistency, same class of race as
    // IterationIntegrationTest#findsAllEventually), so poll with a bounded retry too.
    boolean found =
        pollUntil(
            RUN_LIST_FIND_ATTEMPTS,
            RUN_LIST_FIND_BACKOFF_MILLIS,
            () -> {
              var page =
                  actor
                      .runs()
                      .list(new ListOptions().limit(5L).desc(true), new RunListOptions())
                      .join();
              assertTrue(page.getTotal() >= 0);
              return page.getItems().stream().anyMatch(r -> run.getId().equals(r.getId()));
            });
    assertTrue(found, "expected the just-started run to appear in the Actor's run collection");

    var iterated =
        actor.runs().iterate(new ListOptions().limit(5L).desc(true), new RunListOptions());
    assertTrue(!Publishers.collect(iterated).join().isEmpty());
  }

  @Test
  void callWithActorCallOptionsStreamsLogByDefault() {
    ApifyClient client = requireClient();
    ActorClient actor = client.actor("apify/hello-world");
    List<String> collected = new CopyOnWriteArrayList<>();
    // The log-streaming call() overload (ActorCallOptions) streams the run's log during the wait
    // without an explicit opt-in. runActorAndReadOutputs above covers the plain overload.
    ActorRun run =
        actor
            .call(
                null,
                new ActorCallOptions().logOptions(new StreamedLogOptions().toLog(collected::add)),
                TEST_ACTOR_WAIT_SECS)
            .join();
    assertEquals("SUCCEEDED", run.getStatus());
    // The assertion applies only if the run logged anything.
    assertStreamedLogNonEmptyIfProduced(client.run(run.getId()), collected);
  }

  @Test
  void callWithActorCallOptionsCanDisableLogStreaming() {
    ApifyClient client = requireClient();
    ActorClient actor = client.actor("apify/hello-world");
    ActorRun run =
        actor.call(null, new ActorCallOptions().disableLogStreaming(), TEST_ACTOR_WAIT_SECS).join();
    assertEquals("SUCCEEDED", run.getStatus());
  }

  // Note: this CRUD-style flow deliberately omits a list() step. Unlike Actors/Tasks/Schedules,
  // a run has no natural "list its own kind" collection scoped to itself - the only list() calls
  // for runs are `runs().list()` (account-wide) and the Actor/task-nested `runs()` collection,
  // both of which are already covered by `listRuns` and `actorRunsNestedCollection`.
  @Test
  void runAbortUpdateResurrectDelete() {
    ApifyClient client = requireClient();
    ActorRun run = client.actor("apify/hello-world").start(null, new ActorStartOptions()).join();
    RunClient runClient = client.run(run.getId());

    ActorRun aborted = runClient.abort(false).join();
    assertTrue(!"READY".equals(aborted.getStatus()), aborted.getStatus());
    ActorRun finished = runClient.waitForFinish(60L).join();
    assertTrue(
        finished.isTerminal(), "run did not reach a terminal state: " + finished.getStatus());

    ActorRun updated = runClient.update(Map.of("statusMessage", "integration-test-update")).join();
    assertEquals("integration-test-update", updated.getStatusMessage());

    ActorRun resurrected = runClient.resurrect(new RunResurrectOptions()).join();
    assertTrue(
        !resurrected.isTerminal(),
        "resurrected run should not already be terminal: " + resurrected.getStatus());

    // Clean up the resurrected run so it doesn't linger on the shared test account.
    RunClient resurrectedClient = client.run(resurrected.getId());
    resurrectedClient.abort(false).join();
    // An aborting run cannot be deleted yet (cannot-remove-running-run), so let it finish first.
    resurrectedClient.waitForFinish(60L).join();
    resurrectedClient.delete().join();
  }

  @Test
  void lastRunAccess() {
    ApifyClient client = requireClient();
    ActorRun run =
        client
            .actor("apify/hello-world")
            .call(null, new ActorStartOptions(), TEST_ACTOR_WAIT_SECS)
            .join();
    assertEquals("SUCCEEDED", run.getStatus());

    // `apify/hello-world` is shared, so another run of it by a concurrent test can be "last"
    // between the `call` above and the `lastRun` lookup below. Assert presence, status and origin,
    // not identity.
    var lastRun = client.actor("apify/hello-world").lastRun("SUCCEEDED").get().join();
    assertTrue(lastRun.isPresent());
    assertEquals("SUCCEEDED", lastRun.get().getStatus());

    var byOrigin =
        client
            .actor("apify/hello-world")
            .lastRun(new LastRunOptions().status("SUCCEEDED").origin("API"))
            .get()
            .join();
    assertTrue(byOrigin.isPresent());
    assertEquals("SUCCEEDED", byOrigin.get().getStatus());
    assertEquals("API", byOrigin.get().getMeta().getOrigin());

    // Last-run-scoped nested storage GETs. Whichever run is "last" (see the note above) is a
    // hello-world run, so its default storages exist.
    RunClient lastRunClient = client.actor("apify/hello-world").lastRun("SUCCEEDED");
    lastRunClient.dataset().listItems(new DatasetListItemsOptions()).join();
    lastRunClient.keyValueStore().getRecord("OUTPUT").join();
    assertTrue(lastRunClient.log().get().join().isPresent());
  }

  @Test
  void streamedLogRedirection() {
    ApifyClient client = requireClient();
    ActorRun run = client.actor("apify/hello-world").start(null, new ActorStartOptions()).join();
    RunClient runClient = client.run(run.getId());

    List<String> collected = new CopyOnWriteArrayList<>();
    try (StreamedLog streamedLog =
        runClient.getStreamedLog(new StreamedLogOptions().toLog(collected::add)).join()) {
      streamedLog.start().join();
      runClient.waitForFinish(TEST_ACTOR_WAIT_SECS).join();
    }

    // The assertion applies only if the run logged anything.
    assertStreamedLogNonEmptyIfProduced(runClient, collected);
  }
}
