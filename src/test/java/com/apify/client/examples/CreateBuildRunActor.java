package com.apify.client.examples;

import com.apify.client.ApifyClient;
import com.apify.client.actor.Actor;
import com.apify.client.actor.ActorBuildOptions;
import com.apify.client.actor.ActorClient;
import com.apify.client.actor.ActorStartOptions;
import com.apify.client.build.Build;
import com.apify.client.run.ActorRun;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Demonstrates the full Actor lifecycle: create a new Actor from source files, build it, run it,
 * wait for it to finish, then fetch and print the run log.
 */
public final class CreateBuildRunActor {
  private CreateBuildRunActor() {}

  public static void main(String[] args) {
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));
    // Random, not time-based: this example (like the integration tests) may run concurrently
    // against the same account from several processes/languages at once, and two runs starting in
    // the same millisecond would otherwise collide on the same Actor name.
    String name = "java-example-" + UUID.randomUUID().toString().substring(0, 8);

    Map<String, Object> actorDef =
        Map.of(
            "name",
            name,
            "isPublic",
            false,
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
                                "content",
                                    "console.log('hello from the java client example');")))));

    Actor created = client.actors().create(actorDef).join();
    try {
      System.out.println("Created actor " + created.getId());
      ActorClient actor = client.actor(created.getId());

      Build build = actor.build("0.0", new ActorBuildOptions()).join();
      client.build(build.getId()).waitForFinish(300L).join();
      System.out.println("Built actor (build " + build.getId() + ")");

      ActorRun run = actor.call(null, new ActorStartOptions(), 120L).join();
      System.out.println("Run " + run.getId() + " finished with status " + run.getStatus());

      Optional<String> log = client.run(run.getId()).log().get().join();
      log.ifPresent(text -> System.out.println("--- run log ---\n" + text));
    } finally {
      client.actor(created.getId()).delete().join();
    }
  }
}
