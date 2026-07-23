package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.actor.Actor;
import com.apify.client.actor.ActorBuildOptions;
import com.apify.client.build.Build;
import com.apify.client.build.BuildClient;
import org.junit.jupiter.api.Test;

class BuildIntegrationTest extends IntegrationBase {

  @Test
  void listBuilds() {
    ApifyClient client = requireClient();
    var page = client.builds().list(new ListOptions().limit(5L));
    assertTrue(page.getTotal() >= 0);
  }

  @Test
  void buildActorFlow() {
    ApifyClient client = requireClient();
    Actor created = client.actors().create(ActorIntegrationTest.minimalActor(uniqueName("build")));
    try {
      Build build = client.actor(created.getId()).build("0.0", new ActorBuildOptions());
      Build finished = client.build(build.getId()).waitForFinish(300L);
      assertTrue(finished.isTerminal(), "build did not finish: " + finished.getStatus());

      assertTrue(client.build(build.getId()).get().isPresent());
      client.build(build.getId()).log().get();
      // Exercise the standalone log endpoint (GET /v2/logs/{buildOrRunId}) directly, not only via
      // the build-nested .../log accessor, so the "simple GET per endpoint" rule is met for it.
      assertNotNull(client.log(build.getId()).get());
      client.build(build.getId()).getOpenApiDefinition();
    } finally {
      client.actor(created.getId()).delete();
    }
  }

  @Test
  void buildAbortAndDelete() {
    ApifyClient client = requireClient();
    Actor created =
        client.actors().create(ActorIntegrationTest.minimalActor(uniqueName("build-abort")));
    try {
      Build build = client.actor(created.getId()).build("0.0", new ActorBuildOptions());
      BuildClient buildClient = client.build(build.getId());
      Build aborted = buildClient.abort();
      assertNotNull(aborted.getStatus());
      buildClient.waitForFinish(60L);
      buildClient.delete();
    } finally {
      client.actor(created.getId()).delete();
    }
  }
}
