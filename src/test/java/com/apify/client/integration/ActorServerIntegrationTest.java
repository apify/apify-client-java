package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apify.client.ApifyClient;
import com.apify.client.actor.Actor;
import com.apify.client.actor.ActorBuildOptions;
import com.apify.client.actor.ActorServerClient;
import com.apify.client.actor.ActorServerRequestOptions;
import com.apify.client.build.Build;
import com.apify.client.http.ApiResponse;
import com.apify.client.http.ServerActorException;
import com.apify.client.internal.Json;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Creates a Server Actor that runs an echo web server, builds it, and sends requests to it through
 * {@link ActorServerClient}.
 */
class ActorServerIntegrationTest extends IntegrationBase {

  private static final Map<String, Object> OPENAPI_SPEC =
      Map.of(
          "openapi",
          "3.0.0",
          "info",
          Map.of("title", "Echo server", "version", "1.0.0"),
          "paths",
          Map.of(
              "/echo",
              Map.of(
                  "post",
                  Map.of(
                      "responses", Map.of("200", Map.of("description", "The echoed request."))))));

  /**
   * A web server that answers every request, including the platform's readiness probe, with the
   * request it received, under the status in the {@code status} query parameter, or 200.
   */
  private static final String ECHO_SERVER_SOURCE =
      """
      const http = require('http');

      http.createServer((req, res) => {
          let body = '';
          req.on('data', (chunk) => { body += chunk; });
          req.on('end', () => {
              const status = Number(new URL(req.url, 'http://localhost').searchParams.get('status')) || 200;
              res.writeHead(status, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ method: req.method, url: req.url, headers: req.headers, body }));
          });
      }).listen(process.env.ACTOR_WEB_SERVER_PORT);
      """;

  private static final String DOCKERFILE =
      "FROM node:22-alpine\nCOPY main.js ./\nCMD [\"node\", \"main.js\"]\n";

  /** How long the build of the echo Actor may take, in seconds. */
  private static final long BUILD_WAIT_SECS = 300L;

  private static Map<String, Object> sourceFile(String name, String content) {
    return Map.of("name", name, "format", "TEXT", "content", content);
  }

  private static Actor createEchoServerActor(ApifyClient client, String name) {
    Map<String, Object> actorJson =
        Map.of(
            "actorSpecification",
            1,
            "name",
            name,
            "version",
            "0.0",
            "usesStandbyMode",
            true,
            "webServerSchema",
            OPENAPI_SPEC);
    return client
        .actors()
        .create(
            Map.of(
                "name",
                name,
                "versions",
                List.of(
                    Map.of(
                        "versionNumber",
                        "0.0",
                        "sourceType",
                        "SOURCE_FILES",
                        "buildTag",
                        "latest",
                        "sourceFiles",
                        List.of(
                            sourceFile("Dockerfile", DOCKERFILE),
                            sourceFile("main.js", ECHO_SERVER_SOURCE),
                            sourceFile(
                                ".actor/actor.json",
                                new String(Json.toBytes(actorJson), StandardCharsets.UTF_8))))),
                "actorStandby",
                Map.of("isEnabled", true, "memoryMbytes", 256, "idleTimeoutSecs", 60)))
        .join();
  }

  @Test
  void theServerClientReturnsTheUrlAndSpecificationAndSendsRequestsToTheServer() {
    ApifyClient client = requireClient();
    Actor created = createEchoServerActor(client, uniqueName("server"));
    try {
      Build build = client.actor(created.getId()).build("0.0", new ActorBuildOptions()).join();
      Build finished = client.build(build.getId()).waitForFinish(BUILD_WAIT_SECS).join();
      assertEquals("SUCCEEDED", finished.getStatus());

      Actor actor = client.actor(created.getId()).get().join().orElseThrow();
      ActorServerClient server = client.actor(created.getId()).server();

      assertEquals(actor.getStandbyUrl(), server.getUrl().join());
      Optional<JsonNode> spec = server.getOpenApiSpec().join();
      assertEquals(Optional.of(Json.toTree(OPENAPI_SPEC)), spec);

      ApiResponse response =
          server
              .request(
                  "/echo",
                  new ActorServerRequestOptions()
                      .method("POST")
                      .param("q", "apify")
                      .body(Map.of("limit", 5)))
              .join();

      assertEquals(200, response.statusCode());
      JsonNode echoed = response.bodyAs(JsonNode.class);
      assertEquals("POST", echoed.get("method").asText());
      assertEquals("/echo?q=apify", echoed.get("url").asText());
      assertEquals(
          Json.toTree(Map.of("limit", 5)),
          Json.parse(echoed.get("body").asText().getBytes(StandardCharsets.UTF_8), JsonNode.class));
      // The platform consumes the client's token, so it never reaches the Actor's code.
      assertFalse(echoed.get("headers").has("x-apify-authorization"));
      assertFalse(echoed.get("headers").has("authorization"));

      ApiResponse withOwnAuthorization =
          server
              .request(
                  "/echo",
                  new ActorServerRequestOptions().header("Authorization", "Bearer actor-token"))
              .join();
      assertEquals(
          "Bearer actor-token",
          withOwnAuthorization.bodyAs(JsonNode.class).get("headers").get("authorization").asText());

      ApiResponse errorResponse =
          server.request("/echo", new ActorServerRequestOptions().param("status", 404)).join();
      assertEquals(404, errorResponse.statusCode());
      assertEquals("/echo?status=404", errorResponse.bodyAs(JsonNode.class).get("url").asText());
    } finally {
      client.actor(created.getId()).delete().join();
    }
  }

  @Test
  void anActorThatIsNotAServerActorIsRefused() {
    ApifyClient client = requireClient();
    Actor actor = client.actor("apify/hello-world").get().join().orElseThrow();
    assertNull(actor.getStandbyUrl(), "hello-world does not run in Standby mode");

    CompletionException error =
        assertThrows(
            CompletionException.class,
            () -> client.actor("apify/hello-world").server().getUrl().join());

    assertInstanceOf(ServerActorException.class, error.getCause());
  }
}
