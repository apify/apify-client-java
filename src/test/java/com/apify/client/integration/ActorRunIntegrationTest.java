package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.actor.ActorStartOptions;
import com.apify.client.dataset.DatasetListItemsOptions;
import com.apify.client.log.StreamedLog;
import com.apify.client.log.StreamedLogOptions;
import com.apify.client.run.ActorRun;
import com.apify.client.run.LastRunOptions;
import com.apify.client.run.RunClient;
import com.apify.client.run.RunListOptions;
import com.apify.client.run.RunResurrectOptions;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ActorRunIntegrationTest extends IntegrationBase {

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
    assertTrue(!collected.isEmpty(), "expected redirected log messages");
  }

  /** Bounded window given to {@link #streamedLogRedirection} for the log to catch up. */
  private static final long STREAM_CATCH_UP_TIMEOUT_MILLIS = 15_000;

  /** Poll interval while waiting for the log to catch up in {@link #streamedLogRedirection}. */
  private static final long STREAM_CATCH_UP_POLL_MILLIS = 250;
}
