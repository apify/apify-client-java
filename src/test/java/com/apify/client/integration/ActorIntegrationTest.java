package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.PaginationList;
import com.apify.client.actor.Actor;
import com.apify.client.actor.ActorBuildOptions;
import com.apify.client.actor.ActorClient;
import com.apify.client.actor.ActorEnvVar;
import com.apify.client.actor.ActorListOptions;
import com.apify.client.actor.ActorVersion;
import com.apify.client.build.Build;
import com.apify.client.build.BuildClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActorIntegrationTest extends IntegrationBase {

  /** A minimal Actor definition; the API requires at least one version. */
  static Map<String, Object> minimalActor(String name) {
    return Map.of(
        "name",
        name,
        "isPublic",
        false,
        "description",
        "Integration test actor",
        "versions",
        List.of(
            Map.of(
                "versionNumber", "0.0",
                "sourceType", "SOURCE_FILES",
                "buildTag", "latest",
                "sourceFiles",
                    List.of(
                        Map.of(
                            "name", "Dockerfile",
                            "format", "TEXT",
                            "content", "FROM apify/actor-node:20\nCOPY . ./\nCMD node main.js"),
                        Map.of(
                            "name", "main.js",
                            "format", "TEXT",
                            "content", "console.log('hello from java client test');")))));
  }

  @Test
  void listActors() {
    ApifyClient client = requireClient();
    PaginationList<Actor> page = client.actors().list(new ActorListOptions().my(true).limit(5L));
    assertTrue(page.getTotal() >= 0);
  }

  @Test
  void getActor() {
    ApifyClient client = requireClient();
    Actor created = client.actors().create(minimalActor(uniqueName("get")));
    try {
      var got = client.actor(created.getId()).get();
      assertTrue(got.isPresent());
      assertEquals(created.getId(), got.get().getId());
    } finally {
      client.actor(created.getId()).delete();
    }
  }

  @Test
  void actorCrudFlow() {
    ApifyClient client = requireClient();
    Actor created = client.actors().create(minimalActor(uniqueName("crud")));
    try {
      ActorClient actor = client.actor(created.getId());
      assertTrue(actor.get().isPresent());
      Actor updated = actor.update(Map.of("title", "Updated Title"));
      assertEquals("Updated Title", updated.getTitle());
      actor.builds().list(new ListOptions());
      actor.versions().list(new ListOptions());
    } finally {
      client.actor(created.getId()).delete();
    }
  }

  @Test
  void actorVersionCrudFlow() {
    ApifyClient client = requireClient();
    Actor created = client.actors().create(minimalActor(uniqueName("ver")));
    try {
      ActorClient actor = client.actor(created.getId());
      ActorVersion version =
          actor
              .versions()
              .create(
                  Map.of(
                      "versionNumber", "0.1",
                      "sourceType", "SOURCE_FILES",
                      "buildTag", "latest",
                      "sourceFiles", List.of()));
      assertEquals("0.1", version.getVersionNumber());
      assertTrue(actor.version("0.1").get().isPresent());
      actor.versions().list(new ListOptions());
      actor
          .version("0.1")
          .update(
              Map.of("buildTag", "beta", "sourceType", "SOURCE_FILES", "sourceFiles", List.of()));
      actor.version("0.1").delete();
    } finally {
      client.actor(created.getId()).delete();
    }
  }

  @Test
  void validateInput() {
    ApifyClient client = requireClient();
    // apify/hello-world is a public store Actor; validate-input is read-only and returns
    // {"valid": <bool>}. A well-formed input validates true.
    boolean valid = client.actor("apify/hello-world").validateInput(Map.of("firstNumber", 1));
    assertTrue(valid);
  }

  @Test
  void actorDefaultBuildAndWebhooks() {
    ApifyClient client = requireClient();
    Actor created = client.actors().create(minimalActor(uniqueName("default-build")));
    try {
      ActorClient actor = client.actor(created.getId());
      Build build = actor.build("0.0", new ActorBuildOptions());
      client.build(build.getId()).waitForFinish(300L);

      BuildClient defaultBuild = actor.defaultBuild(60L);
      assertTrue(defaultBuild.get().isPresent());

      // Read-only nested webhook collection (GET + iterate); the account has none registered for
      // this fresh Actor, so this just exercises both calls succeeding against an empty result —
      // not a multi-item case. `NestedWebhookCollectionClient` shares its `list`/`iterate`
      // implementation with `AbstractWebhookCollectionClient`, whose paging/iteration behavior is
      // already exercised with real multi-item data (2 created webhooks, paged one at a time) by
      // `IterationIntegrationTest#iterateWebhooks` through the sibling `WebhookCollectionClient`.
      assertTrue(actor.webhooks().list(new ListOptions()).getTotal() >= 0);
      assertTrue(!actor.webhooks().iterate(new ListOptions()).hasNext());
    } finally {
      client.actor(created.getId()).delete();
    }
  }

  @Test
  void actorEnvVarCrudFlow() {
    ApifyClient client = requireClient();
    Actor created = client.actors().create(minimalActor(uniqueName("env")));
    try {
      ActorClient actor = client.actor(created.getId());
      var envVars = actor.version("0.0").envVars();
      envVars.create(new ActorEnvVar("MY_VAR", "value1"));
      assertTrue(actor.version("0.0").envVar("MY_VAR").get().isPresent());
      envVars.list();
      actor.version("0.0").envVar("MY_VAR").update(new ActorEnvVar("MY_VAR", "value2"));
      actor.version("0.0").envVar("MY_VAR").delete();
    } finally {
      client.actor(created.getId()).delete();
    }
  }
}
