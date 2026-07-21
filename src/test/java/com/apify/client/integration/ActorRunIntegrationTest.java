package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.actor.ActorCallOptions;
import com.apify.client.actor.ActorClient;
import com.apify.client.actor.ActorStartOptions;
import com.apify.client.dataset.DatasetListItemsOptions;
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

class ActorRunIntegrationTest extends IntegrationBase {

  // Note: RunClient.metamorph/reboot/charge are intentionally NOT exercised by a live integration
  // test here, matching the project's existing convention for UserIntegrationTest#updateLimits.
  // `metamorph` requires a second target Actor under the same account with a compatible input
  // schema. `reboot` requires a genuinely *running* container; the store Actor exercised in this
  // suite (hello-world) finishes in ~1-2s, so a live reboot test would be either flaky-by-
  // construction or need a bespoke slow-running test Actor this suite doesn't otherwise need.
  // `charge` only applies to pay-per-event Actors, which none of this suite's fixtures are. All
  // three are covered offline instead, against a mock backend, by
  // ClientBehaviourRegressionTest#metamorphSendsTargetActorIdBuildAndInputBody,
  // #rebootSendsPostToRebootWithNoBody, and #chargeSendsIdempotencyKey (+ its sibling charge*
  // tests) respectively.

  @Test
  void listRuns() {
    ApifyClient client = requireClient();
    var page = client.runs().list(new ListOptions().limit(5L), new RunListOptions());
    assertTrue(page.getTotal() >= 0);
  }

  @Test
  void runActorAndReadOutputs() {
    ApifyClient client = requireClient();
    ActorRun run =
        client.actor("apify/hello-world").call(null, new ActorStartOptions(), TEST_ACTOR_WAIT_SECS);
    assertEquals("SUCCEEDED", run.getStatus());

    assertTrue(client.run(run.getId()).get().isPresent());

    Optional<String> log = client.run(run.getId()).log().get();
    assertTrue(log.isPresent() && !log.get().isEmpty());

    client.run(run.getId()).dataset().listItems(new DatasetListItemsOptions());
    client.run(run.getId()).keyValueStore().getRecord("OUTPUT");
    client.run(run.getId()).requestQueue().listHead(null);

    // Nested run-storage metadata GETs (previously untested): confirm the run's default storages
    // are reachable as full metadata resources, not just via the item/record/head-level calls
    // above.
    assertTrue(client.run(run.getId()).dataset().get().isPresent());
    assertTrue(client.run(run.getId()).keyValueStore().get().isPresent());
    assertTrue(client.run(run.getId()).requestQueue().get().isPresent());

    // A few more run-scoped GETs not otherwise exercised on this particular (run-nested) storage
    // path; the same operations are already covered end-to-end against standalone storages
    // elsewhere (DatasetIntegrationTest/KeyValueStoreIntegrationTest/RequestQueueIntegrationTest),
    // this only confirms they also work when reached through `run().<storage>()`.
    client.run(run.getId()).dataset().getStatistics();
    client.run(run.getId()).keyValueStore().listKeys(new ListKeysOptions());
    client.run(run.getId()).requestQueue().listRequests(new ListRequestsOptions());
    // hello-world's default request queue is empty, so a made-up id is expected to resolve to
    // nothing; this still exercises the live GET-by-id code path through the run-nested client.
    assertTrue(client.run(run.getId()).requestQueue().getRequest("does-not-exist").isEmpty());

    // Typed getters (previously only reachable via getExtra()): verify the API's response
    // actually deserializes into them, not just that the code compiles.
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
    ActorRun run = actor.call(null, new ActorStartOptions(), TEST_ACTOR_WAIT_SECS);

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
                  actor.runs().list(new ListOptions().limit(5L).desc(true), new RunListOptions());
              assertTrue(page.getTotal() >= 0);
              return page.getItems().stream().anyMatch(r -> run.getId().equals(r.getId()));
            });
    assertTrue(found, "expected the just-started run to appear in the Actor's run collection");

    var iterated =
        actor.runs().iterate(new ListOptions().limit(5L).desc(true), new RunListOptions());
    assertTrue(iterated.hasNext());
  }

  @Test
  void callWithActorCallOptionsStreamsLogByDefault() {
    ApifyClient client = requireClient();
    ActorClient actor = client.actor("apify/hello-world");
    List<String> collected = new CopyOnWriteArrayList<>();
    // The log-streaming call() overload (ActorCallOptions), matching the reference client's
    // default call(options.log='default') behavior: the run's log is streamed for the duration of
    // the wait without any explicit opt-in. runActorAndReadOutputs above exercises the plain
    // (non-streaming) ActorStartOptions overload.
    ActorRun run =
        actor.call(
            null,
            new ActorCallOptions().logOptions(new StreamedLogOptions().toLog(collected::add)),
            TEST_ACTOR_WAIT_SECS);
    assertEquals("SUCCEEDED", run.getStatus());
    assertTrue(!collected.isEmpty(), "expected the default call() to have streamed log lines");
  }

  @Test
  void callWithActorCallOptionsCanDisableLogStreaming() {
    ApifyClient client = requireClient();
    ActorClient actor = client.actor("apify/hello-world");
    ActorRun run =
        actor.call(null, new ActorCallOptions().disableLogStreaming(), TEST_ACTOR_WAIT_SECS);
    assertEquals("SUCCEEDED", run.getStatus());
  }

  // Note: this CRUD-style flow deliberately omits a list() step. Unlike Actors/Tasks/Schedules,
  // a run has no natural "list its own kind" collection scoped to itself - the only list() calls
  // for runs are `runs().list()` (account-wide) and the Actor/task-nested `runs()` collection,
  // both of which are already covered by `listRuns` and `actorRunsNestedCollection`.
  @Test
  void runAbortUpdateResurrectDelete() {
    ApifyClient client = requireClient();
    ActorRun run = client.actor("apify/hello-world").start(null, new ActorStartOptions());
    RunClient runClient = client.run(run.getId());

    ActorRun aborted = runClient.abort(false);
    assertTrue(!"READY".equals(aborted.getStatus()), aborted.getStatus());
    ActorRun finished = runClient.waitForFinish(60L);
    assertTrue(
        finished.isTerminal(), "run did not reach a terminal state: " + finished.getStatus());

    ActorRun updated = runClient.update(Map.of("statusMessage", "integration-test-update"));
    assertEquals("integration-test-update", updated.getStatusMessage());

    ActorRun resurrected = runClient.resurrect(new RunResurrectOptions());
    assertTrue(
        !resurrected.isTerminal(),
        "resurrected run should not already be terminal: " + resurrected.getStatus());

    // Clean up the resurrected run so it doesn't linger on the shared test account.
    RunClient resurrectedClient = client.run(resurrected.getId());
    resurrectedClient.abort(false);
    resurrectedClient.delete();
  }

  @Test
  void lastRunAccess() {
    ApifyClient client = requireClient();
    ActorRun run =
        client.actor("apify/hello-world").call(null, new ActorStartOptions(), TEST_ACTOR_WAIT_SECS);
    assertEquals("SUCCEEDED", run.getStatus());

    // `apify/hello-world` is a shared public store Actor: under the account's concurrent-execution
    // isolation contract, other runs of that same Actor (e.g. a sibling-language client's suite
    // running at the same time against the same test user) can legitimately be "last" between the
    // `call` above and the `lastRun` lookup below. Assert presence/status/origin, not identity
    // with the run just started.
    var lastRun = client.actor("apify/hello-world").lastRun("SUCCEEDED").get();
    assertTrue(lastRun.isPresent());
    assertEquals("SUCCEEDED", lastRun.get().getStatus());

    var byOrigin =
        client
            .actor("apify/hello-world")
            .lastRun(new LastRunOptions().status("SUCCEEDED").origin("API"))
            .get();
    assertTrue(byOrigin.isPresent());
    assertEquals("SUCCEEDED", byOrigin.get().getStatus());
    assertEquals("API", byOrigin.get().getMeta().getOrigin());

    // Last-run-scoped nested storage GETs (previously untested — only `lastRun().get()` itself
    // was): `actor(...).lastRun(...)` returns a RunClient, and its nested storage clients must be
    // reachable the same way `run(id).<storage>()`'s are. Whichever run resolves as "last" (see
    // the concurrency note above) is a hello-world run, so its default storages are guaranteed to
    // exist.
    RunClient lastRunClient = client.actor("apify/hello-world").lastRun("SUCCEEDED");
    lastRunClient.dataset().listItems(new DatasetListItemsOptions());
    lastRunClient.keyValueStore().getRecord("OUTPUT");
    assertTrue(lastRunClient.log().get().isPresent());
  }

  @Test
  void streamedLogRedirection() {
    ApifyClient client = requireClient();
    ActorRun run = client.actor("apify/hello-world").start(null, new ActorStartOptions());
    RunClient runClient = client.run(run.getId());

    List<String> collected = new CopyOnWriteArrayList<>();
    try (StreamedLog streamedLog =
        runClient.getStreamedLog(new StreamedLogOptions().toLog(collected::add))) {
      streamedLog.start();
      runClient.waitForFinish(TEST_ACTOR_WAIT_SECS);
      // A fast Actor (hello-world routinely finishes in a couple of seconds) can complete before
      // the background reader has pulled any bytes off the live log stream yet, even though the
      // log content itself is already fully available server-side once the run is done. Rather
      // than asserting immediately (a race with that background thread) or closing the stream
      // right away (which would cut the reader off before its first read), give it a bounded
      // window to catch up and flush; the log is static at this point, so waiting longer never
      // helps once it is genuinely empty.
      pollUntil(STREAM_CATCH_UP_ATTEMPTS, STREAM_CATCH_UP_POLL_MILLIS, () -> !collected.isEmpty());
    }

    // The catch-up window above closes an eventual-consistency race, but not a genuine one: the
    // *first* stream was opened before the run finished, following the live log as the run was
    // still writing to it, so the underlying HTTP stream can reach EOF (the container/log-follow
    // connection closing) a hair before the final bytes are flushed to that same connection -- no
    // amount of client-side waiting after that EOF recovers bytes that were never delivered on it.
    // Ask the authoritative source - the run's persisted log via the plain (non-streaming)
    // log().get() call - whether the run produced any log output at all, and if the first stream
    // still came up empty despite that, retry once with a brand-new stream opened strictly after
    // the run is already finished: that is no longer a live tail, just a GET against a static,
    // fully-persisted log, so it cannot race the run's own writer the way the first stream could.
    Optional<String> authoritativeLog = runClient.log().get();
    boolean runProducedLog = authoritativeLog.isPresent() && !authoritativeLog.get().isEmpty();
    if (runProducedLog && collected.isEmpty()) {
      collected.addAll(collectFinishedRunLog(runClient));
    }
    if (runProducedLog) {
      assertTrue(
          !collected.isEmpty(),
          "run produced a non-empty log ("
              + authoritativeLog.get().length()
              + " chars) but the"
              + " streamed collector observed none - redirection did not work");
    }
  }

  /**
   * Opens a fresh {@link StreamedLog} against an already-finished run's static log (with {@link
   * StreamedLogOptions#fromStart(boolean)} so the run's already-past-relative-to-construction
   * messages are not filtered out) and waits up to {@link #STREAM_CATCH_UP_TIMEOUT_MILLIS} for it
   * to deliver the (now non-racing) content, so {@link #streamedLogRedirection} can retry once
   * after the first, live-tail stream comes up empty despite the run having produced a log.
   */
  private static List<String> collectFinishedRunLog(RunClient runClient) {
    List<String> retryCollected = new CopyOnWriteArrayList<>();
    try (StreamedLog retryStream =
        runClient.getStreamedLog(
            new StreamedLogOptions().toLog(retryCollected::add).fromStart(true))) {
      retryStream.start();
      pollUntil(
          STREAM_CATCH_UP_ATTEMPTS, STREAM_CATCH_UP_POLL_MILLIS, () -> !retryCollected.isEmpty());
    }
    return retryCollected;
  }
}
