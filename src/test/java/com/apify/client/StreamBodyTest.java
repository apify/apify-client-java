package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.actor.ActorServerRequestOptions;
import com.apify.client.actor.ActorStartOptions;
import com.apify.client.http.ApifyApiException;
import com.apify.client.http.ForbiddenError;
import com.apify.client.http.ServerActorException;
import com.apify.client.http.ServerError;
import com.apify.client.keyvalue.KeyValueStoreRecordStream;
import com.apify.client.keyvalue.SetRecordOptions;
import com.apify.client.run.ActorRun;
import com.apify.client.run.MetamorphOptions;
import com.apify.client.task.TaskStartOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests of the request bodies that are {@link InputStream}s and of the streamed record read,
 * against a local HTTP server, so the chunked upload, the headers and the redirects are real
 * network behavior. Token-free.
 */
class StreamBodyTest {

  private static final String RECORD_PATH = "/v2/key-value-stores/s1/records/key";
  private static final String RUN_JSON = "{\"data\":{\"id\":\"r1\",\"status\":\"READY\"}}";
  private static final String MEMORY_LIMIT_JSON =
      "{\"error\":{\"type\":\"actor-memory-limit-exceeded\",\"message\":\"not enough memory\"}}";

  /** A body larger than the compression threshold that compresses well. */
  private static final byte[] LARGE_BODY = "a".repeat(20_000).getBytes(StandardCharsets.UTF_8);

  private TestHttpServer server;

  /**
   * Counts the reads of the records that fail once, so the first read fails and the retry works.
   */
  private final AtomicInteger flakyReads = new AtomicInteger();

  private final AtomicInteger droppedReads = new AtomicInteger();

  /**
   * The statuses the server answers record uploads with, one per request, before it answers 200.
   */
  private final List<Integer> uploadStatuses = Collections.synchronizedList(new ArrayList<>());

  @BeforeEach
  void startServer() throws IOException {
    server = new TestHttpServer(this::answer);
  }

  @AfterEach
  void stopServer() throws IOException {
    server.close();
  }

  private TestHttpServer.Reply answer(TestHttpServer.Received request) {
    String path = request.path();
    if (path.equals(RECORD_PATH) && request.method().equals("PUT") && !uploadStatuses.isEmpty()) {
      return TestHttpServer.Reply.json(uploadStatuses.remove(0), "{\"error\":{\"type\":\"x\"}}");
    }
    if (path.equals(RECORD_PATH) && request.method().equals("GET")) {
      return TestHttpServer.Reply.of(200, "text/plain", "streamed record");
    }
    if (path.endsWith("/records/flaky") && flakyReads.getAndIncrement() == 0) {
      return TestHttpServer.Reply.json(503, "{\"error\":{\"type\":\"x\"}}");
    }
    if (path.endsWith("/records/dropped") && droppedReads.getAndIncrement() == 0) {
      return TestHttpServer.Reply.drop();
    }
    if (path.endsWith("/records/flaky") || path.endsWith("/records/dropped")) {
      return TestHttpServer.Reply.of(200, "text/plain", "after retry");
    }
    if (path.endsWith("/records/forbidden")) {
      return TestHttpServer.Reply.json(
          403, "{\"error\":{\"type\":\"forbidden\",\"message\":\"no access\"}}");
    }
    if (path.equals("/v2/actors/plain")) {
      return TestHttpServer.Reply.json(200, "{\"data\":{\"id\":\"plain\"}}");
    }
    if (path.endsWith("/records/missing")) {
      return TestHttpServer.Reply.json(
          404, "{\"error\":{\"type\":\"record-not-found\",\"message\":\"no record\"}}");
    }
    if (path.endsWith("/records/redirected")) {
      return TestHttpServer.Reply.redirect(307, server.url() + RECORD_PATH);
    }
    if (path.equals("/v2/actors/memory-limited/runs")) {
      return TestHttpServer.Reply.json(400, MEMORY_LIMIT_JSON);
    }
    if (path.endsWith("/runs")) {
      return TestHttpServer.Reply.json(201, RUN_JSON);
    }
    if (path.equals("/v2/actors/server")) {
      return TestHttpServer.Reply.json(
          200,
          "{\"data\":{\"id\":\"server\",\"actorStandby\":{\"isEnabled\":true},"
              + "\"standbyUrl\":\""
              + server.url()
              + "/server\"}}");
    }
    if (path.equals("/server/flaky")) {
      return TestHttpServer.Reply.json(503, "{}");
    }
    return TestHttpServer.Reply.json(200, "{}");
  }

  private ApifyClient client() {
    return ApifyClient.builder()
        .token("test-token")
        .baseUrl(server.url())
        .maxRetries(3)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  private static InputStream streamOf(byte[] bytes) {
    return new ByteArrayInputStream(bytes);
  }

  private TestHttpServer.Received onlyRequestTo(String path) {
    List<TestHttpServer.Received> matching =
        server.received().stream().filter(r -> r.path().equals(path)).toList();
    assertEquals(1, matching.size(), "requests to " + path + ": " + matching);
    return matching.get(0);
  }

  private long requestsTo(String path) {
    return server.received().stream().filter(r -> r.path().equals(path)).count();
  }

  private static Throwable rootOf(Throwable error) {
    Throwable current = error;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  // ---- key-value store record upload -----------------------------------------------------------

  @Test
  void setRecordSendsTheStreamAsTheBodyWithItsContentType() {
    byte[] bytes = {1, 2, 3, 4, 5};
    client()
        .keyValueStore("s1")
        .setRecord("key", streamOf(bytes), "application/octet-stream")
        .join();

    TestHttpServer.Received request = onlyRequestTo(RECORD_PATH);
    assertEquals("PUT", request.method());
    assertArrayEquals(bytes, request.body());
    assertEquals("application/octet-stream", request.header("Content-Type"));
  }

  @Test
  void setRecordDoesNotCompressAStreamButCompressesTheSameBytesAsAnArray() {
    client().keyValueStore("s1").setRecord("key", streamOf(LARGE_BODY), "text/plain").join();
    TestHttpServer.Received streamed = server.received().get(0);
    assertNull(streamed.header("Content-Encoding"), "a stream is sent as it is");
    assertArrayEquals(LARGE_BODY, streamed.body());

    client().keyValueStore("s1").setRecord("key", LARGE_BODY, "text/plain").join();
    TestHttpServer.Received buffered = server.received().get(1);
    assertEquals("br", buffered.header("Content-Encoding"), "the default compression");
  }

  @Test
  void setRecordForwardsTheContentEncodingOptionOfAStream() {
    client()
        .keyValueStore("s1")
        .setRecord(
            "key",
            streamOf(new byte[] {1}),
            "text/plain",
            new SetRecordOptions().contentEncoding("br"))
        .join();
    assertEquals("br", onlyRequestTo(RECORD_PATH).header("Content-Encoding"));
  }

  @Test
  void setRecordRejectsABlankContentEncodingOfAStream() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            client()
                .keyValueStore("s1")
                .setRecord(
                    "key",
                    streamOf(new byte[] {1}),
                    "text/plain",
                    new SetRecordOptions().contentEncoding(" ")));
    assertTrue(server.received().isEmpty());
  }

  @Test
  void setRecordDoesNotRetryAStreamOnAServerError() {
    uploadStatuses.add(503);
    CompletionException error =
        assertThrows(
            CompletionException.class,
            () ->
                client()
                    .keyValueStore("s1")
                    .setRecord("key", streamOf(new byte[] {1, 2}), "text/plain")
                    .join());
    assertInstanceOf(ServerError.class, rootOf(error));
    assertEquals(1, server.received().size(), "a stream cannot be sent twice, so no retry");
  }

  @Test
  void setRecordRetriesTheSameBytesAsAnArray() {
    uploadStatuses.add(503);
    client().keyValueStore("s1").setRecord("key", new byte[] {1, 2}, "text/plain").join();
    assertEquals(2, server.received().size(), "an array is sent again after the 503");
  }

  @Test
  void setRecordDoesNotFollowARedirectWithAStream() {
    CompletionException error =
        assertThrows(
            CompletionException.class,
            () ->
                client()
                    .keyValueStore("s1")
                    .setRecord("redirected", streamOf(new byte[] {1, 2, 3}), "text/plain")
                    .join());
    assertEquals(307, assertInstanceOf(ApifyApiException.class, rootOf(error)).getStatusCode());
    assertEquals(0, requestsTo(RECORD_PATH), "the redirect target is never contacted");
  }

  // ---- closing the stream ----------------------------------------------------------------------

  /** A stream that records whether it was closed. */
  private static final class TrackedStream extends ByteArrayInputStream {
    private final AtomicBoolean closed = new AtomicBoolean();

    TrackedStream() {
      super(new byte[] {1, 2, 3});
    }

    @Override
    public void close() throws IOException {
      closed.set(true);
      super.close();
    }
  }

  @Test
  void aStreamBodyIsClosedAfterASuccessfulCall() {
    TrackedStream stream = new TrackedStream();
    client().keyValueStore("s1").setRecord("key", stream, "text/plain").join();
    assertTrue(stream.closed.get());
  }

  @Test
  void aStreamBodyIsClosedAfterAFailedCall() {
    uploadStatuses.add(503);
    TrackedStream stream = new TrackedStream();
    assertThrows(
        CompletionException.class,
        () -> client().keyValueStore("s1").setRecord("key", stream, "text/plain").join());
    assertTrue(stream.closed.get());
  }

  @Test
  void aStreamBodyIsClosedWhenTheConnectionFails() throws IOException {
    String unreachable = server.url();
    server.close();
    ApifyClient client =
        ApifyClient.builder().token("t").baseUrl(unreachable).maxRetries(0).build();
    TrackedStream stream = new TrackedStream();
    assertThrows(
        CompletionException.class,
        () -> client.actor("a1").start(stream, new ActorStartOptions()).join());
    assertTrue(stream.closed.get());
  }

  @Test
  void aStreamBodyIsClosedWhenTheActorIsNotAServerActor() {
    TrackedStream stream = new TrackedStream();
    CompletionException error =
        assertThrows(
            CompletionException.class,
            () ->
                client()
                    .actor("plain")
                    .server()
                    .request("/", new ActorServerRequestOptions().method("POST").body(stream))
                    .join());
    assertInstanceOf(ServerActorException.class, rootOf(error));
    assertTrue(stream.closed.get());
  }

  // ---- streamed record read --------------------------------------------------------------------

  @Test
  void getRecordStreamCompletesWithTheLiveBodyAndItsContentType() throws IOException {
    Optional<KeyValueStoreRecordStream> record =
        client().keyValueStore("s1").getRecordStream("key").join();

    try (KeyValueStoreRecordStream stream = record.orElseThrow()) {
      assertEquals("key", stream.getKey());
      assertEquals("text/plain", stream.getContentType());
      assertEquals(
          "streamed record", new String(stream.getValue().readAllBytes(), StandardCharsets.UTF_8));
    }
    assertTrue(onlyRequestTo(RECORD_PATH).query().contains("attachment=1"));
  }

  @Test
  void getRecordStreamCompletesWithEmptyForAMissingRecord() {
    assertTrue(client().keyValueStore("s1").getRecordStream("missing").join().isEmpty());
  }

  @Test
  void getRecordStreamRetriesARetryableStatus() throws IOException {
    try (KeyValueStoreRecordStream record =
        client().keyValueStore("s1").getRecordStream("flaky").join().orElseThrow()) {
      assertEquals(
          "after retry", new String(record.getValue().readAllBytes(), StandardCharsets.UTF_8));
    }
    assertEquals(2, requestsTo("/v2/key-value-stores/s1/records/flaky"));
  }

  @Test
  void getRecordStreamRetriesATransportError() throws IOException {
    try (KeyValueStoreRecordStream record =
        client().keyValueStore("s1").getRecordStream("dropped").join().orElseThrow()) {
      assertEquals(
          "after retry", new String(record.getValue().readAllBytes(), StandardCharsets.UTF_8));
    }
    assertEquals(2, requestsTo("/v2/key-value-stores/s1/records/dropped"));
  }

  @Test
  void getRecordStreamThrowsOnAnErrorStatusThatIsNotRetried() {
    CompletionException error =
        assertThrows(
            CompletionException.class,
            () -> client().keyValueStore("s1").getRecordStream("forbidden").join());
    assertInstanceOf(ForbiddenError.class, rootOf(error));
    assertEquals(1, requestsTo("/v2/key-value-stores/s1/records/forbidden"));
  }

  @Test
  void aRecordStreamCanBePassedStraightToSetRecord() throws IOException {
    ApifyClient client = client();
    try (KeyValueStoreRecordStream source =
        client.keyValueStore("s1").getRecordStream("key").join().orElseThrow()) {
      client
          .keyValueStore("s2")
          .setRecord("copy", source.getValue(), source.getContentType())
          .join();
    }
    TestHttpServer.Received upload =
        server.received().stream().filter(r -> r.method().equals("PUT")).findFirst().orElseThrow();
    assertEquals("streamed record", upload.bodyText());
    assertEquals("text/plain", upload.header("Content-Type"));
  }

  // ---- Actor and task input --------------------------------------------------------------------

  @Test
  void actorStartSendsAStreamInputAsItIs() {
    byte[] input = "{\"query\":\"cats\"}".getBytes(StandardCharsets.UTF_8);
    ActorRun run = client().actor("a1").start(streamOf(input), new ActorStartOptions()).join();

    assertEquals("r1", run.getId());
    TestHttpServer.Received request = onlyRequestTo("/v2/actors/a1/runs");
    assertArrayEquals(input, request.body(), "the stream is not serialized to JSON");
    assertEquals("application/json", request.header("Content-Type"));
  }

  @Test
  void actorStartTakesTheContentTypeOfAStreamInputFromTheOptions() {
    client()
        .actor("a1")
        .start(streamOf(new byte[] {1}), new ActorStartOptions().contentType("application/xml"))
        .join();
    assertEquals("application/xml", onlyRequestTo("/v2/actors/a1/runs").header("Content-Type"));
  }

  @Test
  void actorStartDoesNotWaitForResourcesWithAStreamInput() {
    CompletionException error =
        assertThrows(
            CompletionException.class,
            () ->
                client()
                    .actor("memory-limited")
                    .start(streamOf(new byte[] {1}), new ActorStartOptions().waitForResources(true))
                    .join());
    ApifyApiException api = assertInstanceOf(ApifyApiException.class, rootOf(error));
    assertEquals("actor-memory-limit-exceeded", api.getType());
    assertEquals(1, server.received().size(), "a stream cannot be sent again, so no retry");
  }

  @Test
  void taskStartSendsAStreamInputAsItIs() {
    byte[] input = {9, 8, 7};
    client().task("t1").start(streamOf(input), new TaskStartOptions()).join();
    assertArrayEquals(input, onlyRequestTo("/v2/actor-tasks/t1/runs").body());
  }

  @Test
  void methodsThatTakeNoStreamRejectOneInsteadOfSendingMeaninglessJson() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            client()
                .run("r1")
                .metamorph("other", streamOf(new byte[] {1}), new MetamorphOptions()));
    assertThrows(
        IllegalArgumentException.class,
        () -> client().actor("a1").validateInput(streamOf(new byte[] {1})));
    assertTrue(server.received().isEmpty());
  }

  // ---- Server Actor request --------------------------------------------------------------------

  @Test
  void serverRequestSendsAStreamBodyAsItIs() {
    byte[] body = "hello server".getBytes(StandardCharsets.UTF_8);
    client()
        .actor("server")
        .server()
        .request("/echo", new ActorServerRequestOptions().method("POST").body(streamOf(body)))
        .join();
    TestHttpServer.Received request = onlyRequestTo("/server/echo");
    assertArrayEquals(body, request.body());
    assertNull(request.header("Content-Type"), "a stream is sent as it is, with no assumed type");
  }

  @Test
  void serverRequestDoesNotRetryAStreamBodyOnA503() {
    var response =
        client()
            .actor("server")
            .server()
            .request(
                "/flaky",
                new ActorServerRequestOptions().method("POST").body(streamOf(new byte[] {1})))
            .join();
    assertEquals(503, response.statusCode());
    assertEquals(1, requestsTo("/server/flaky"));
  }

  @Test
  void serverRequestRetriesABufferedBodyOnA503() {
    client()
        .actor("server")
        .server()
        .request("/flaky", new ActorServerRequestOptions().method("POST").body(new byte[] {1}))
        .join();
    assertTrue(requestsTo("/server/flaky") > 1);
  }

  @Test
  void serverRequestStreamSendsAStreamBodyAsItIs() throws IOException {
    byte[] body = "hello streamed".getBytes(StandardCharsets.UTF_8);
    try (var response =
        client()
            .actor("server")
            .server()
            .requestStream(
                "/echo", new ActorServerRequestOptions().method("POST").body(streamOf(body)))
            .join()) {
      assertEquals(200, response.statusCode());
    }
    assertArrayEquals(body, onlyRequestTo("/server/echo").body());
  }
}
