package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.actor.ActorClient;
import com.apify.client.actor.ActorServerClient;
import com.apify.client.actor.ActorServerRequestOptions;
import com.apify.client.actor.ActorServerStreamResponse;
import com.apify.client.http.ApiResponse;
import com.apify.client.http.ApifyTransportException;
import com.apify.client.http.DefaultHttpTransport;
import com.apify.client.http.GzipHttpCompressor;
import com.apify.client.http.HttpTransport;
import com.apify.client.http.NotFoundError;
import com.apify.client.http.ServerActorException;
import com.apify.client.http.Timeout;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.Json;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Tests of {@link ActorServerClient} against a local HTTP server that plays both the Apify API and
 * the Actor's server, so the headers, redirects and dropped connections are real network behavior.
 * Token-free.
 */
class ActorServerClientTest {

  private static final String ACTOR_ID = "test-actor-id";
  private static final String ACTOR_PATH = "/v2/actors/" + ACTOR_ID;
  private static final String SERVER_PREFIX = "/server";
  private static final JsonNode OPENAPI_SPEC =
      Json.toTree(Map.of("openapi", "3.0.0", "paths", Map.of()));
  private static final String TOKEN = "test-token";

  private TestHttpServer server;

  /** The Actor the API returns; null answers 404, as for an Actor that does not exist. */
  private Map<String, Object> actor;

  /** Build numbers of the Actor's builds by build id, newest first, as the builds list has them. */
  private Map<String, String> builds;

  /** How many builds the builds list returns per page. */
  private int buildsPerPage;

  /** The {@code actorDefinition} every build the API returns has; null for none. */
  private Map<String, Object> actorDefinition;

  /** Statuses the server answers {@code /flaky} with, one per request, before it answers 200. */
  private final List<Integer> flakyStatuses = Collections.synchronizedList(new ArrayList<>());

  @BeforeEach
  void startServer() throws IOException {
    server = new TestHttpServer(this::answer);
    actor = serverActor(Map.of());
    builds = new LinkedHashMap<>();
    buildsPerPage = Integer.MAX_VALUE;
    actorDefinition = Map.of("webServerSchema", Json.toTree(OPENAPI_SPEC));
  }

  @AfterEach
  void stopServer() throws IOException {
    server.close();
  }

  private Map<String, Object> serverActor(Map<String, Object> fields) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", ACTOR_ID);
    result.put("name", "test-actor");
    result.put("actorStandby", Map.of("isEnabled", true));
    result.put(
        "taggedBuilds",
        Map.of(
            "latest", Map.of("buildId", "latest-build-id", "buildNumber", "0.0.2"),
            "beta", Map.of("buildId", "beta-build-id", "buildNumber", "0.0.3")));
    result.put("standbyUrl", server.url() + SERVER_PREFIX);
    result.putAll(fields);
    return result;
  }

  private TestHttpServer.Reply answer(TestHttpServer.Received request) {
    String path = request.path();
    if (path.equals(ACTOR_PATH)) {
      return actor == null
          ? TestHttpServer.Reply.json(
              404,
              "{\"error\":{\"type\":\"record-not-found\",\"message\":\"Actor was not found\"}}")
          : data(actor);
    }
    if (path.equals(ACTOR_PATH + "/builds")) {
      return buildList(request);
    }
    if (path.startsWith("/v2/actor-builds/")) {
      Map<String, Object> build = new LinkedHashMap<>();
      build.put("id", path.substring(path.lastIndexOf('/') + 1));
      build.put("actorDefinition", actorDefinition);
      return data(build);
    }
    if (path.equals(SERVER_PREFIX + "/flaky") && !flakyStatuses.isEmpty()) {
      return TestHttpServer.Reply.json(flakyStatuses.remove(0), "{\"error\":\"flaky\"}");
    }
    if (path.equals(SERVER_PREFIX + "/hang-up")) {
      return TestHttpServer.Reply.drop();
    }
    if (path.equals(SERVER_PREFIX + "/missing")) {
      return TestHttpServer.Reply.of(404, "text/plain", "Not here");
    }
    return TestHttpServer.Reply.json(200, "{\"ok\":true}");
  }

  private TestHttpServer.Reply buildList(TestHttpServer.Received request) {
    long offset = 0;
    for (String pair : request.query().split("&")) {
      if (pair.startsWith("offset=")) {
        offset = Long.parseLong(pair.substring("offset=".length()));
      }
    }
    List<Map<String, Object>> all = new ArrayList<>();
    builds.forEach((id, number) -> all.add(Map.of("id", id, "buildNumber", number)));
    int from = (int) Math.min(offset, all.size());
    List<Map<String, Object>> page =
        all.subList(from, (int) Math.min(all.size(), (long) from + buildsPerPage));
    return data(
        Map.of(
            "total",
            all.size(),
            "offset",
            offset,
            "limit",
            1000,
            "count",
            page.size(),
            "items",
            page));
  }

  private static TestHttpServer.Reply data(Object payload) {
    return TestHttpServer.Reply.json(
        200, new String(Json.toBytes(Map.of("data", payload)), StandardCharsets.UTF_8));
  }

  private ApifyClient client(int maxRetries) {
    return ApifyClient.builder()
        .token(TOKEN)
        .baseUrl(server.url())
        .maxRetries(maxRetries)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  private ApifyClient client() {
    return client(0);
  }

  private List<TestHttpServer.Received> serverRequests() {
    return server.received().stream()
        .filter(request -> request.path().startsWith(SERVER_PREFIX + "/"))
        .toList();
  }

  private long actorGets() {
    return server.received().stream().filter(request -> request.path().equals(ACTOR_PATH)).count();
  }

  private static Throwable rootOf(Throwable error) {
    Throwable current = error;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static <T extends Throwable> T failureOf(Class<T> type, CompletableFuture<?> future) {
    Throwable error = assertThrows(CompletionException.class, future::join);
    return assertInstanceOf(type, rootOf(error));
  }

  // ---- request -----------------------------------------------------------------------------

  @Test
  void requestSendsTheMethodPathQueryBodyAndTokenToTheServerUrl() {
    ApiResponse response =
        client()
            .actor(ACTOR_ID)
            .server()
            .request(
                "/search",
                new ActorServerRequestOptions()
                    .method("POST")
                    .param("q", "apify")
                    .body(Map.of("limit", 5)))
            .join();

    assertEquals(200, response.statusCode());
    assertEquals(Json.toTree(Map.of("ok", true)), response.bodyAs(JsonNode.class));
    assertEquals(1, serverRequests().size());
    TestHttpServer.Received request = serverRequests().get(0);
    assertEquals("POST", request.method());
    assertEquals(SERVER_PREFIX + "/search", request.path());
    assertEquals("q=apify", request.query());
    assertEquals(Json.toTree(Map.of("limit", 5)), Json.parse(request.body(), JsonNode.class));
    assertEquals("application/json", request.header("Content-Type"));
    assertEquals("Bearer " + TOKEN, request.header("X-Apify-Authorization"));
    assertNull(request.header("Authorization"));
  }

  @Test
  void requestSendsAnAuthorizationHeaderOfTheCallerAsItIs() {
    client()
        .actor(ACTOR_ID)
        .server()
        .request("/", new ActorServerRequestOptions().header("Authorization", "Bearer actor-token"))
        .join();

    TestHttpServer.Received request = serverRequests().get(0);
    assertEquals("Bearer actor-token", request.header("Authorization"));
    assertEquals("Bearer " + TOKEN, request.header("X-Apify-Authorization"));
  }

  @Test
  void requestReturnsARedirectOfTheServerAsItIsSoTheTokenNeverReachesItsTarget()
      throws IOException {
    for (HttpTransport transport :
        List.of(
            new DefaultHttpTransport(),
            new DefaultHttpTransport(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()))) {
      AtomicBoolean redirected = new AtomicBoolean();
      try (TestHttpServer other =
              new TestHttpServer(
                  request -> {
                    redirected.set(true);
                    return TestHttpServer.Reply.json(200, "{}");
                  });
          TestHttpServer redirecting =
              new TestHttpServer(request -> TestHttpServer.Reply.redirect(other.url() + "/"))) {
        actor = serverActor(Map.of("standbyUrl", redirecting.url()));
        ApifyClient client =
            ApifyClient.builder()
                .token(TOKEN)
                .baseUrl(server.url())
                .httpTransport(transport)
                .maxRetries(0)
                .build();

        ApiResponse response = client.actor(ACTOR_ID).server().request("/").join();

        assertEquals(302, response.statusCode());
        assertEquals(other.url() + "/", response.headers().firstValue("Location").orElseThrow());
        assertFalse(redirected.get(), "the redirect target must never be contacted");
      }
    }
  }

  @Test
  void requestReusesTheServerUrlFetchedByTheFirstCall() {
    ActorClient actorClient = client().actor(ACTOR_ID);

    actorClient.server().request().join();
    actorClient.server().request().join();

    assertEquals(1, actorGets());
    assertEquals(
        List.of(SERVER_PREFIX + "/", SERVER_PREFIX + "/"),
        serverRequests().stream().map(TestHttpServer.Received::path).toList());
  }

  @Test
  void getUrlReturnsTheStandbyUrlOfTheActorAndFetchesItOnlyOnce() {
    ActorClient actorClient = client().actor(ACTOR_ID);

    assertEquals(server.url() + SERVER_PREFIX, actorClient.server().getUrl().join());
    assertEquals(server.url() + SERVER_PREFIX, actorClient.server().getUrl().join());

    assertEquals(1, actorGets());
  }

  @Test
  void getUrlFetchesTheActorOnceForConcurrentFirstCalls() {
    ActorServerClient serverClient = client().actor(ACTOR_ID).server();

    CompletableFuture<String> url = serverClient.getUrl();
    CompletableFuture<ApiResponse> response = serverClient.request();
    CompletableFuture.allOf(url, response).join();

    assertEquals(server.url() + SERVER_PREFIX, url.join());
    assertEquals(1, actorGets());
  }

  @Test
  void getUrlFetchesTheActorAgainAfterAFailedLookup() {
    ActorServerClient serverClient = client().actor(ACTOR_ID).server();
    Map<String, Object> existing = actor;
    actor = null;

    failureOf(NotFoundError.class, serverClient.getUrl());
    actor = existing;

    assertEquals(server.url() + SERVER_PREFIX, serverClient.getUrl().join());
    assertEquals(2, actorGets());
  }

  @Test
  void concurrentFirstCallsAllSeeTheErrorOfAFailedLookup() throws InterruptedException {
    actor = null;
    ActorServerClient serverClient = client().actor(ACTOR_ID).server();
    int callers = 8;
    List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < callers; i++) {
      Thread thread =
          new Thread(
              () -> {
                try {
                  serverClient.getUrl().join();
                } catch (Throwable error) {
                  failures.add(rootOf(error));
                }
              });
      threads.add(thread);
    }
    threads.forEach(Thread::start);
    for (Thread thread : threads) {
      thread.join();
    }

    assertEquals(callers, failures.size());
    failures.forEach(failure -> assertInstanceOf(NotFoundError.class, failure));
  }

  @Test
  void cancellingTheFutureOfGetUrlLeavesTheSharedLookupAlone() {
    ActorServerClient serverClient = client().actor(ACTOR_ID).server();

    serverClient.getUrl().cancel(true);

    assertEquals(server.url() + SERVER_PREFIX, serverClient.getUrl().join());
  }

  @Test
  void requestReusesTheServerUrlFetchedByGetOpenApiSpec() {
    ActorServerClient serverClient = client().actor(ACTOR_ID).server();

    serverClient.getOpenApiSpec().join();
    serverClient.request().join();

    assertEquals(1, actorGets());
  }

  @Test
  void callsFailForAnActorThatIsNotAServerActor() {
    actor = serverActor(new LinkedHashMap<>(Collections.singletonMap("standbyUrl", null)));
    ActorServerClient serverClient = client().actor(ACTOR_ID).server();

    for (CompletableFuture<?> call :
        List.of(serverClient.getUrl(), serverClient.getOpenApiSpec(), serverClient.request())) {
      ServerActorException error = failureOf(ServerActorException.class, call);
      assertTrue(error.getMessage().contains("is not a Server Actor"), error.getMessage());
    }
    assertEquals(0, serverRequests().size());
  }

  @Test
  void requestFailsWithNotFoundForAnActorThatDoesNotExist() {
    actor = null;

    failureOf(NotFoundError.class, client().actor(ACTOR_ID).server().request());
  }

  @Test
  void requestSendsAPathThatLooksLikeAnotherHostToTheServerAsAPathOnIt() {
    for (String path :
        List.of("//evil.example/steal", "@evil.example/steal", "https://evil.example/steal")) {
      server.received().clear();

      ApiResponse response = client().actor(ACTOR_ID).server().request(path).join();

      assertEquals(200, response.statusCode(), path);
      assertEquals(1, serverRequests().size(), path);
    }
  }

  @Test
  void requestDropsAFragmentInThePathSoTheParamsStillReachTheServer() {
    client()
        .actor(ACTOR_ID)
        .server()
        .request("/search#results", new ActorServerRequestOptions().param("q", "apify"))
        .join();

    TestHttpServer.Received request = serverRequests().get(0);
    assertEquals(SERVER_PREFIX + "/search", request.path());
    assertEquals("q=apify", request.query());
  }

  @Test
  void requestConvertsTheQueryParametersAsForApiCalls() {
    client()
        .actor(ACTOR_ID)
        .server()
        .request(
            "/",
            new ActorServerRequestOptions()
                .param("flag", true)
                .param("off", false)
                .param("tags", List.of("a", "b"))
                .param("skipped", null)
                .param("limit", 5))
        .join();

    assertEquals("flag=1&off=0&tags=a%2Cb&limit=5", serverRequests().get(0).query());
  }

  // ---- body ---------------------------------------------------------------------------------

  private static Map<String, Object> largePayload() {
    return Map.of("items", Collections.nCopies(100, "x".repeat(100)));
  }

  @Test
  void requestSendsABodyLargeEnoughToCompressUncompressed() {
    Map<String, Object> payload = largePayload();

    client()
        .actor(ACTOR_ID)
        .server()
        .request("/items", new ActorServerRequestOptions().method("POST").body(payload))
        .join();

    TestHttpServer.Received request = serverRequests().get(0);
    assertNull(request.header("Content-Encoding"));
    assertEquals(Json.toTree(payload), Json.parse(request.body(), JsonNode.class));
  }

  @Test
  void requestCompressesTheBodyWhenACompressorIsSet() throws IOException {
    Map<String, Object> payload = largePayload();

    client()
        .actor(ACTOR_ID)
        .server()
        .request(
            "/items",
            new ActorServerRequestOptions()
                .method("POST")
                .body(payload)
                .compression(new GzipHttpCompressor(1)))
        .join();

    TestHttpServer.Received request = serverRequests().get(0);
    assertEquals("gzip", request.header("Content-Encoding"));
    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(request.body()))) {
      assertEquals(Json.toTree(payload), Json.parse(in.readAllBytes(), JsonNode.class));
    }
  }

  @Test
  void requestSendsAContentEncodingHeaderOfTheCallerWithTheBodyUntouched() throws IOException {
    byte[] raw = "x".repeat(2000).getBytes(StandardCharsets.UTF_8);
    java.io.ByteArrayOutputStream compressed = new java.io.ByteArrayOutputStream();
    try (GZIPOutputStream out = new GZIPOutputStream(compressed)) {
      out.write(raw);
    }

    client()
        .actor(ACTOR_ID)
        .server()
        .request(
            "/items",
            new ActorServerRequestOptions()
                .method("POST")
                .header("content-encoding", "gzip")
                .body(compressed.toByteArray())
                .compression(new GzipHttpCompressor(1)))
        .join();

    TestHttpServer.Received request = serverRequests().get(0);
    assertEquals("gzip", request.header("Content-Encoding"));
    assertTrue(java.util.Arrays.equals(compressed.toByteArray(), request.body()));
  }

  @Test
  void requestSendsAStringBodyAsItIsWithTheContentTypeOfTheCaller() {
    client()
        .actor(ACTOR_ID)
        .server()
        .request(
            "/echo",
            new ActorServerRequestOptions()
                .method("PUT")
                .header("Content-Type", "text/plain")
                .body("plain text"))
        .join();

    TestHttpServer.Received request = serverRequests().get(0);
    assertEquals("PUT", request.method());
    assertEquals("text/plain", request.header("Content-Type"));
    assertEquals("plain text", request.bodyText());
  }

  @Test
  void optionsRejectAnUnknownMethodAndATimeoutThatIsNull() {
    ActorServerRequestOptions options = new ActorServerRequestOptions();

    assertThrows(IllegalArgumentException.class, () -> options.method("get"));
    assertThrows(IllegalArgumentException.class, () -> options.method("CONNECT"));
    assertThrows(IllegalArgumentException.class, () -> options.method(null));
    assertThrows(IllegalArgumentException.class, () -> options.timeout(null));
    assertEquals(0, server.received().size());
  }

  // ---- responses and retries ----------------------------------------------------------------

  @Test
  void requestReturnsAnErrorResponseOfTheServerAsItIs() {
    ApiResponse response = client().actor(ACTOR_ID).server().request("/missing").join();

    assertEquals(404, response.statusCode());
    assertEquals("Not here", response.bodyAsString());
  }

  @Test
  void requestRetriesARateLimitAndServiceUnavailable() {
    for (int status : List.of(429, 503)) {
      server.received().clear();
      flakyStatuses.clear();
      flakyStatuses.addAll(List.of(status, status));

      ApiResponse response = client(2).actor(ACTOR_ID).server().request("/flaky").join();

      assertEquals(200, response.statusCode(), "status " + status);
      assertEquals(3, serverRequests().size(), "status " + status);
    }
  }

  @Test
  void requestReturnsTheLastRetriedResponseOnceTheRetriesRunOut() {
    flakyStatuses.addAll(List.of(503, 503, 503));

    ApiResponse response = client(1).actor(ACTOR_ID).server().request("/flaky").join();

    assertEquals(503, response.statusCode());
    assertEquals(2, serverRequests().size());
  }

  @Test
  void requestReturnsAServerErrorOtherThan503WithoutRetryingIt() {
    flakyStatuses.add(500);

    ApiResponse response =
        client(2)
            .actor(ACTOR_ID)
            .server()
            .request("/flaky", new ActorServerRequestOptions().method("POST").body(Map.of("a", 1)))
            .join();

    assertEquals(500, response.statusCode());
    assertEquals(1, serverRequests().size());
  }

  @Test
  void requestDoesNotRetryATransportErrorSinceTheActorMayHaveAlreadyHandledTheRequest() {
    CompletableFuture<ApiResponse> call =
        client(2)
            .actor(ACTOR_ID)
            .server()
            .request(
                "/hang-up", new ActorServerRequestOptions().method("POST").body(Map.of("a", 1)));

    failureOf(ApifyTransportException.class, call);
    assertEquals(1, serverRequests().size());
  }

  @Test
  void apiCallsStillThrowOnAnErrorStatusAndRetryIt() {
    actor = null;

    failureOf(NotFoundError.class, client(2).actor(ACTOR_ID).server().getUrl());
    assertEquals(1, actorGets(), "a 404 is not retried");
  }

  // ---- streaming ----------------------------------------------------------------------------

  @Test
  void requestStreamReturnsTheBodyUnreadOfAnyResponse() throws IOException {
    ActorServerClient serverClient = client().actor(ACTOR_ID).server();

    try (ActorServerStreamResponse response = serverClient.requestStream("/", null).join()) {
      assertEquals(200, response.statusCode());
      assertEquals(
          Json.toTree(Map.of("ok", true)),
          Json.parse(response.body().readAllBytes(), JsonNode.class));
    }
    try (ActorServerStreamResponse response =
        serverClient.requestStream("/missing", new ActorServerRequestOptions()).join()) {
      assertEquals(404, response.statusCode());
      assertEquals("Not here", new String(response.body().readAllBytes(), StandardCharsets.UTF_8));
    }
    TestHttpServer.Received request = serverRequests().get(0);
    assertEquals("Bearer " + TOKEN, request.header("X-Apify-Authorization"));
    assertNull(request.header("Authorization"));
  }

  @Test
  void requestStreamRetriesARateLimitAndServiceUnavailable() throws IOException {
    flakyStatuses.addAll(List.of(429, 503));

    try (ActorServerStreamResponse response =
        client(2).actor(ACTOR_ID).server().requestStream("/flaky", null).join()) {
      assertEquals(200, response.statusCode());
      assertEquals(
          Json.toTree(Map.of("ok", true)),
          Json.parse(response.body().readAllBytes(), JsonNode.class));
    }
    assertEquals(3, serverRequests().size());
  }

  @Test
  void requestStreamReturnsTheLastRetriedResponseOnceTheRetriesRunOut() throws IOException {
    flakyStatuses.addAll(List.of(503, 503, 503));

    try (ActorServerStreamResponse response =
        client(1).actor(ACTOR_ID).server().requestStream("/flaky", null).join()) {
      assertEquals(503, response.statusCode());
      assertEquals(
          "{\"error\":\"flaky\"}",
          new String(response.body().readAllBytes(), StandardCharsets.UTF_8));
    }
    assertEquals(2, serverRequests().size());
  }

  @Test
  void requestStreamDoesNotFollowARedirect() throws IOException {
    try (TestHttpServer redirecting =
        new TestHttpServer(request -> TestHttpServer.Reply.redirect("http://127.0.0.1:1/"))) {
      actor = serverActor(Map.of("standbyUrl", redirecting.url()));

      try (ActorServerStreamResponse response =
          client().actor(ACTOR_ID).server().requestStream("/", null).join()) {
        assertEquals(302, response.statusCode());
      }
    }
  }

  // ---- transports that cannot hold back redirects -------------------------------------------

  @Test
  void aTransportThatCannotDisableRedirectsIsNotTrustedWithARequestToAServer() {
    MockTransport backend =
        MockTransport.ofRouter(
            request ->
                request.uri().getPath().equals("/v2/actors/a")
                    ? MockTransport.ok(
                        200,
                        "{\"data\":{\"id\":\"a\",\"standbyUrl\":\"https://a--b.apify.actor\"}}")
                    : MockTransport.ok(200, "{}"));
    ApifyClient client =
        ApifyClient.builder().token(TOKEN).httpTransport(backend).maxRetries(0).build();

    CompletableFuture<ApiResponse> call = client.actor("a").server().request("/x");

    Throwable cause = rootOf(assertThrows(CompletionException.class, call::join));
    assertInstanceOf(ApifyTransportException.class, cause);
    assertInstanceOf(UnsupportedOperationException.class, cause.getCause());
    assertEquals(1, backend.calls, "only the Actor lookup was sent, never the server request");
  }

  // ---- URL cache and cancellation ---------------------------------------------------------

  /** A transport that delegates to a mock and supports requests that do not follow redirects. */
  private record RedirectAwareTransport(MockTransport mock) implements HttpTransport {
    @Override
    public CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest request) {
      return mock.sendAsync(request);
    }

    @Override
    public CompletableFuture<HttpResponse<InputStream>> sendStreamingAsync(HttpRequest request) {
      return mock.sendStreamingAsync(request);
    }

    @Override
    public CompletableFuture<HttpResponse<byte[]>> sendAsync(
        HttpRequest request, boolean followRedirects) {
      return mock.sendAsync(request);
    }

    @Override
    public CompletableFuture<HttpResponse<InputStream>> sendStreamingAsync(
        HttpRequest request, boolean followRedirects) {
      return mock.sendStreamingAsync(request);
    }
  }

  /** Cancelling a request after the URL was looked up by {@code warmUp} cancels the HTTP call. */
  private void assertCancellingARequestReachesTheHttpCall(Consumer<ActorServerClient> warmUp) {
    MockTransport backend =
        MockTransport.ofRouter(
            request -> {
              String path = request.uri().getPath();
              if (path.equals("/v2/actors/a")) {
                return MockTransport.ok(
                    200,
                    "{\"data\":{\"id\":\"a\",\"standbyUrl\":\"https://a--b.apify.actor\","
                        + "\"taggedBuilds\":{\"latest\":{\"buildId\":\"b1\"}}}}");
              }
              return MockTransport.ok(200, "{\"data\":{\"id\":\"b1\"}}");
            },
            request -> request.uri().getHost().equals("a--b.apify.actor"));
    ApifyClient client =
        ApifyClient.builder()
            .token(TOKEN)
            .httpTransport(new RedirectAwareTransport(backend))
            .maxRetries(0)
            .build();
    ActorServerClient serverClient = client.actor("a").server();
    warmUp.accept(serverClient);

    CompletableFuture<ApiResponse> call = serverClient.request("/x");
    assertEquals(1, backend.hung.size(), "the server request is in flight");
    call.cancel(true);

    assertTrue(backend.allHungCancelled(), "cancelling the request must cancel the HTTP call");
  }

  @Test
  void cancellingARequestReachesTheHttpCallAfterGetUrl() {
    assertCancellingARequestReachesTheHttpCall(serverClient -> serverClient.getUrl().join());
  }

  @Test
  void cancellingARequestReachesTheHttpCallAfterGetOpenApiSpec() {
    assertCancellingARequestReachesTheHttpCall(
        serverClient -> serverClient.getOpenApiSpec().join());
  }

  @Test
  void aNullTimeoutIsRejectedAndLeavesTheClientUsable() {
    ActorServerClient serverClient = client().actor(ACTOR_ID).server();

    assertThrows(IllegalArgumentException.class, () -> serverClient.getUrl(null));
    assertThrows(IllegalArgumentException.class, () -> serverClient.getOpenApiSpec(null));

    assertEquals(server.url() + SERVER_PREFIX, serverClient.getUrl().join());
    assertEquals(200, serverClient.request().join().statusCode());
  }

  /** A transport whose first call throws instead of returning a future, then serves for real. */
  private static final class ThrowingOnceTransport implements HttpTransport {
    private final HttpTransport delegate = new DefaultHttpTransport();
    private final AtomicBoolean thrown = new AtomicBoolean();

    @Override
    public CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest request) {
      if (thrown.compareAndSet(false, true)) {
        throw new IllegalStateException("the transport failed before it returned a future");
      }
      return delegate.sendAsync(request);
    }

    @Override
    public CompletableFuture<HttpResponse<InputStream>> sendStreamingAsync(HttpRequest request) {
      return delegate.sendStreamingAsync(request);
    }

    @Override
    public CompletableFuture<HttpResponse<byte[]>> sendAsync(
        HttpRequest request, boolean followRedirects) {
      return followRedirects ? sendAsync(request) : delegate.sendAsync(request, false);
    }

    @Override
    public CompletableFuture<HttpResponse<InputStream>> sendStreamingAsync(
        HttpRequest request, boolean followRedirects) {
      return delegate.sendStreamingAsync(request, followRedirects);
    }
  }

  @Test
  void aLookupThatFailsWhileStartingIsNotKept() throws Exception {
    ApifyClient client =
        ApifyClient.builder()
            .token(TOKEN)
            .baseUrl(server.url())
            .httpTransport(new ThrowingOnceTransport())
            .maxRetries(0)
            .build();
    ActorServerClient serverClient = client.actor(ACTOR_ID).server();

    failureOf(IllegalStateException.class, serverClient.getUrl());

    assertEquals(server.url() + SERVER_PREFIX, serverClient.getUrl().get(10, TimeUnit.SECONDS));
  }

  // ---- OpenAPI specification ----------------------------------------------------------------

  @Test
  void getOpenApiSpecReturnsTheWebServerSchemaOfTheTaggedBuild() {
    Map<String, Optional<String>> buildTags = new LinkedHashMap<>();
    buildTags.put("latest", Optional.empty());
    buildTags.put("beta", Optional.of("beta"));
    buildTags.put("no build set uses latest", null);
    for (Map.Entry<String, Optional<String>> tag : buildTags.entrySet()) {
      Map<String, Object> standby = new LinkedHashMap<>();
      standby.put("isEnabled", true);
      if (tag.getValue() != null && tag.getValue().isPresent()) {
        standby.put("build", tag.getValue().get());
      }
      actor = serverActor(Map.of("actorStandby", standby));
      server.received().clear();

      Optional<JsonNode> spec = client().actor(ACTOR_ID).server().getOpenApiSpec().join();

      assertEquals(Optional.of(OPENAPI_SPEC), spec, tag.getKey());
      String expectedBuild = "beta".equals(tag.getKey()) ? "beta-build-id" : "latest-build-id";
      assertEquals(
          "/v2/actor-builds/" + expectedBuild,
          server.received().get(server.received().size() - 1).path(),
          tag.getKey());
    }
  }

  @Test
  void getOpenApiSpecLooksABuildNumberUpInTheBuildsOfTheActor() {
    actor = serverActor(Map.of("actorStandby", Map.of("isEnabled", true, "build", "0.0.1")));
    builds.put("latest-build-id", "0.0.2");
    builds.put("old-build-id", "0.0.1");

    Optional<JsonNode> spec = client().actor(ACTOR_ID).server().getOpenApiSpec().join();

    assertEquals(Optional.of(OPENAPI_SPEC), spec);
    assertEquals(
        "/v2/actor-builds/old-build-id",
        server.received().get(server.received().size() - 1).path());
  }

  @Test
  void getUrlAndGetOpenApiSpecTakeTheTimeoutOfTheCaller() {
    MockTransport backend =
        MockTransport.ofRouter(
            request -> {
              String path = request.uri().getPath();
              if (path.equals("/v2/actors/a")) {
                return MockTransport.ok(
                    200,
                    "{\"data\":{\"id\":\"a\",\"standbyUrl\":\"https://a--b.apify.actor\","
                        + "\"actorStandby\":{\"build\":\"0.0.1\"}}}");
              }
              if (path.equals("/v2/actors/a/builds")) {
                return MockTransport.ok(
                    200,
                    "{\"data\":{\"total\":1,\"offset\":0,\"limit\":1,\"count\":1,"
                        + "\"items\":[{\"id\":\"b1\",\"buildNumber\":\"0.0.1\"}]}}");
              }
              return MockTransport.ok(200, "{\"data\":{\"id\":\"b1\"}}");
            });
    ApifyClient client =
        ApifyClient.builder().token(TOKEN).httpTransport(backend).maxRetries(0).build();
    Timeout timeout = Timeout.ofSeconds(7);

    client.actor("a").server().getOpenApiSpec(timeout).join();
    assertEquals(3, backend.calls, "the Actor, the builds list and the build");
    assertEquals(
        List.of(Optional.of(Duration.ofSeconds(7))), backend.timeouts.stream().distinct().toList());

    backend.timeouts.clear();
    client.actor("a").server().getUrl(timeout).join();
    assertEquals(List.of(Optional.of(Duration.ofSeconds(7))), backend.timeouts);
  }

  @Test
  void getOpenApiSpecReturnsEmptyForABuildWithoutAWebServerSchema() {
    actorDefinition = Map.of("name", "test-actor");
    assertEquals(Optional.empty(), client().actor(ACTOR_ID).server().getOpenApiSpec().join());

    actorDefinition = null;
    assertEquals(Optional.empty(), client().actor(ACTOR_ID).server().getOpenApiSpec().join());
  }

  @Test
  void getOpenApiSpecFailsForABuildThatIsNeitherATagNorAListedBuildNumber() {
    actor = serverActor(Map.of("actorStandby", Map.of("isEnabled", true, "build", "9.9.9")));
    builds.put("latest-build-id", "0.0.2");

    ServerActorException error =
        failureOf(ServerActorException.class, client().actor(ACTOR_ID).server().getOpenApiSpec());

    assertEquals("Build 9.9.9 of Actor test-actor-id does not exist.", error.getMessage());
  }

  @Test
  void getOpenApiSpecStopsTheBuildNumberLookupAtAnOlderBuildOfTheSameVersion() {
    actor = serverActor(Map.of("actorStandby", Map.of("isEnabled", true, "build", "0.0.4")));
    builds.put("build-5", "0.0.5");
    builds.put("build-3", "0.0.3");
    builds.put("build-2", "0.0.2");
    builds.put("build-1", "0.0.1");
    buildsPerPage = 1;

    failureOf(ServerActorException.class, client().actor(ACTOR_ID).server().getOpenApiSpec());

    assertEquals(2, buildListRequests(), "build-5, then build-3 which is older than 0.0.4");
  }

  @Test
  void getOpenApiSpecFailsForAnUnknownTagWithoutListingTheBuilds() {
    actor = serverActor(Map.of("actorStandby", Map.of("isEnabled", true, "build", "nightly")));

    ServerActorException error =
        failureOf(ServerActorException.class, client().actor(ACTOR_ID).server().getOpenApiSpec());

    assertEquals("Build nightly of Actor test-actor-id does not exist.", error.getMessage());
    assertEquals(0, buildListRequests());
  }

  private long buildListRequests() {
    return server.received().stream()
        .filter(request -> request.path().equals(ACTOR_PATH + "/builds"))
        .count();
  }

  // ---- ActorClient --------------------------------------------------------------------------

  @Test
  @SuppressWarnings("deprecation")
  void standbyReturnsTheSameServerClientAsServer() {
    ActorClient actorClient = client().actor(ACTOR_ID);

    ActorServerClient standby = actorClient.standby();

    assertNotNull(standby);
    assertSame(standby, actorClient.server());
    assertEquals(200, standby.request("/search").join().statusCode());
  }

  @Test
  void actorModelExposesTheStandbyUrl() {
    assertEquals(
        server.url() + SERVER_PREFIX,
        client().actor(ACTOR_ID).get().join().orElseThrow().getStandbyUrl());
  }

  // ---- internal HTTP core -------------------------------------------------------------------

  @Test
  void theDefaultApiCallKeepsTheTokenInTheAuthorizationHeader() {
    client().actor(ACTOR_ID).get().join();

    TestHttpServer.Received request = server.received().get(0);
    assertEquals("Bearer " + TOKEN, request.header("Authorization"));
    assertNull(request.header("X-Apify-Authorization"));
    assertEquals(HttpClientCore.MAX_SUCCESS_STATUS, 300);
  }
}
