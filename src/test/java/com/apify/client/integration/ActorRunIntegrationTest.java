package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.actor.ActorCallOptions;
import com.apify.client.actor.ActorClient;
import com.apify.client.actor.ActorStartOptions;
import com.apify.client.dataset.DatasetListItemsOptions;
import com.apify.client.log.StreamedLog;
import com.apify.client.log.StreamedLogOptions;
import com.apify.client.run.ActorRun;
import com.apify.client.run.LastRunOptions;
import com.apify.client.run.RunClient;
import com.apify.client.run.RunListOptions;
import com.apify.client.run.RunResurrectOptions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    ActorRun run = client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L);
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

    // Typed getters (previously only reachable via getExtra()): verify the API's response
    // actually deserializes into them, not just that the code compiles.
    assertTrue(run.getGeneralAccess() != null);
    assertTrue(run.getStats() != null);
    assertTrue(run.getOptions() != null && run.getOptions().getBuild() != null);
    assertTrue(run.getMeta() != null && run.getMeta().getOrigin() != null);
    assertTrue(run.getUsage() != null);
  }

  @Test
  void actorRunsNestedCollection() throws InterruptedException {
    ApifyClient client = requireClient();
    ActorClient actor = client.actor("apify/hello-world");
    ActorRun run = actor.call(null, new ActorStartOptions(), 120L);

    // Runs are sorted ascending by startedAt by default, and "apify/hello-world" is a heavily
    // used public Actor, so the just-started run would never surface within a small default-order
    // page; request newest-first. The LIST endpoint's index can also lag a just-finished run by a
    // moment (eventual consistency, same class of race as
    // IterationIntegrationTest#findsAllEventually), so poll with a bounded retry too.
    boolean found = false;
    for (int attempt = 0; !found && attempt < 10; attempt++) {
      var page = actor.runs().list(new ListOptions().limit(5L).desc(true), new RunListOptions());
      assertTrue(page.getTotal() >= 0);
      found = page.getItems().stream().anyMatch(r -> run.getId().equals(r.getId()));
      if (!found) {
        Thread.sleep(1000);
      }
    }
    assertTrue(found, "expected the just-started run to appear in the Actor's run collection");

    var iterated =
        actor.runs().iterate(new ListOptions().limit(5L).desc(true), new RunListOptions());
    assertTrue(iterated.hasNext());
  }

  @Test
  void callWithActorCallOptionsStreamsLogByDefault() {
    ApifyClient client = requireClient();
    ActorClient actor = client.actor("apify/hello-world");
    List<String> collected = new java.util.concurrent.CopyOnWriteArrayList<>();
    // The log-streaming call() overload (ActorCallOptions), matching the reference client's
    // default call(options.log='default') behavior: the run's log is streamed for the duration of
    // the wait without any explicit opt-in. runActorAndReadOutputs above exercises the plain
    // (non-streaming) ActorStartOptions overload.
    ActorRun run =
        actor.call(
            null,
            new ActorCallOptions().logOptions(new StreamedLogOptions().toLog(collected::add)),
            120L);
    assertEquals("SUCCEEDED", run.getStatus());
    assertTrue(!collected.isEmpty(), "expected the default call() to have streamed log lines");
  }

  @Test
  void callWithActorCallOptionsCanDisableLogStreaming() {
    ApifyClient client = requireClient();
    ActorClient actor = client.actor("apify/hello-world");
    ActorRun run = actor.call(null, new ActorCallOptions().disableLogStreaming(), 120L);
    assertEquals("SUCCEEDED", run.getStatus());
  }

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
    ActorRun run = client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L);

    var lastRun = client.actor("apify/hello-world").lastRun("SUCCEEDED").get();
    assertTrue(lastRun.isPresent());
    assertEquals("SUCCEEDED", lastRun.get().getStatus());
    // Assert identity, not just status: the "last" run resolved above must actually be the run
    // just created above, not merely some other SUCCEEDED run on the account.
    assertEquals(run.getId(), lastRun.get().getId());

    var byOrigin =
        client
            .actor("apify/hello-world")
            .lastRun(new LastRunOptions().status("SUCCEEDED").origin("API"))
            .get();
    assertTrue(byOrigin.isPresent());
    assertEquals("SUCCEEDED", byOrigin.get().getStatus());
    assertEquals(run.getId(), byOrigin.get().getId());
  }

  @Test
  void streamedLogRedirection() {
    ApifyClient client = requireClient();
    ActorRun run = client.actor("apify/hello-world").start(null, new ActorStartOptions());
    RunClient runClient = client.run(run.getId());

    java.util.List<String> collected = new java.util.concurrent.CopyOnWriteArrayList<>();
    try (StreamedLog streamedLog =
        runClient.getStreamedLog(new StreamedLogOptions().toLog(collected::add))) {
      streamedLog.start();
      runClient.waitForFinish(120L);
      // A fast Actor (hello-world routinely finishes in a couple of seconds) can complete before
      // the background reader has pulled any bytes off the live log stream yet, even though the
      // log content itself is already fully available server-side once the run is done. Rather
      // than asserting immediately (a race with that background thread) or closing the stream
      // right away (which would cut the reader off before its first read), give it a bounded
      // window to catch up and flush; the log is static at this point, so waiting longer never
      // helps once it is genuinely empty.
      long deadline = System.currentTimeMillis() + STREAM_CATCH_UP_TIMEOUT_MILLIS;
      while (collected.isEmpty() && System.currentTimeMillis() < deadline) {
        try {
          Thread.sleep(STREAM_CATCH_UP_POLL_MILLIS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
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
  private static java.util.List<String> collectFinishedRunLog(RunClient runClient) {
    java.util.List<String> retryCollected = new java.util.concurrent.CopyOnWriteArrayList<>();
    try (StreamedLog retryStream =
        runClient.getStreamedLog(
            new StreamedLogOptions().toLog(retryCollected::add).fromStart(true))) {
      retryStream.start();
      long deadline = System.currentTimeMillis() + STREAM_CATCH_UP_TIMEOUT_MILLIS;
      while (retryCollected.isEmpty() && System.currentTimeMillis() < deadline) {
        try {
          Thread.sleep(STREAM_CATCH_UP_POLL_MILLIS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    return retryCollected;
  }

  /** Bounded window given to {@link #streamedLogRedirection} for the log to catch up. */
  private static final long STREAM_CATCH_UP_TIMEOUT_MILLIS = 15_000;

  /** Poll interval while waiting for the log to catch up in {@link #streamedLogRedirection}. */
  private static final long STREAM_CATCH_UP_POLL_MILLIS = 250;
}
