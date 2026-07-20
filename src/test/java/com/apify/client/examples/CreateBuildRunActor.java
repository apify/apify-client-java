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

/**
 * Demonstrates the full Actor lifecycle: create a new Actor from source files, build it, run it,
 * wait for it to finish, then fetch and print the run log.
 */
public final class CreateBuildRunActor {
  private CreateBuildRunActor() {}

  public static void main(String[] args) {
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));
    String name = "java-example-" + System.currentTimeMillis();

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

    Actor created = client.actors().create(actorDef);
    try {
      System.out.println("Created actor " + created.getId());
      ActorClient actor = client.actor(created.getId());

      Build build = actor.build("0.0", new ActorBuildOptions());
      client.build(build.getId()).waitForFinish(300L);
      System.out.println("Built actor (build " + build.getId() + ")");

      ActorRun run = actor.call(null, new ActorStartOptions(), 120L);
      System.out.println("Run " + run.getId() + " finished with status " + run.getStatus());

      Optional<String> log = client.run(run.getId()).log().get();
      log.ifPresent(text -> System.out.println("--- run log ---\n" + text));
    } finally {
      client.actor(created.getId()).delete();
    }
  }
}
