package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ActorRun;
import com.apify.client.ActorStartOptions;
import com.apify.client.ApifyClient;
import com.apify.client.LastRunOptions;
import com.apify.client.ListOptions;
import com.apify.client.RunListOptions;
import com.apify.client.RunOrigin;
import com.apify.client.RunStatus;
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
    assertEquals(RunStatus.SUCCEEDED, run.getStatus());

    assertTrue(client.run(run.getId()).get().isPresent());

    Optional<String> log = client.run(run.getId()).log().get();
    assertTrue(log.isPresent() && !log.get().isEmpty());

    client.run(run.getId()).dataset().listItems(new com.apify.client.DatasetListItemsOptions());
    client.run(run.getId()).keyValueStore().getRecord("OUTPUT");
  }

  @Test
  void lastRunAccess() {
    ApifyClient client = requireClient();
    client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L);

    var lastRun = client.actor("apify/hello-world").lastRun(RunStatus.SUCCEEDED).get();
    assertTrue(lastRun.isPresent());
    assertEquals(RunStatus.SUCCEEDED, lastRun.get().getStatus());

    var byOrigin =
        client
            .actor("apify/hello-world")
            .lastRun(new LastRunOptions().status(RunStatus.SUCCEEDED).origin(RunOrigin.API))
            .get();
    assertTrue(byOrigin.isPresent());
    assertEquals(RunStatus.SUCCEEDED, byOrigin.get().getStatus());
  }
}
