package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.Actor;
import com.apify.client.ActorBuildOptions;
import com.apify.client.ApifyClient;
import com.apify.client.Build;
import com.apify.client.ListOptions;
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
      client.build(build.getId()).getOpenApiDefinition();
    } finally {
      client.actor(created.getId()).delete();
    }
  }
}
