package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.actor.ActorListOptions;
import com.apify.client.actor.ActorStartOptions;
import com.apify.client.requestqueue.RequestQueueRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * Cancelling a future the client returned cancels the work behind it: the request in flight, the
 * retries, the polling and the pages that would follow.
 */
class CancellationTest {

  private static final String RUN_JSON = "{\"data\":{\"id\":\"r1\",\"status\":\"RUNNING\"}}";

  private static ApifyClient client(com.apify.client.http.HttpTransport backend, int maxRetries) {
    return ApifyClient.builder()
        .token("t")
        .httpTransport(backend)
        .maxRetries(maxRetries)
        .minDelayBetweenRetries(Duration.ofMillis(300))
        .build();
  }

  private static void await(BooleanSupplier condition) throws InterruptedException {
    for (int i = 0; i < 200 && !condition.getAsBoolean(); i++) {
      Thread.sleep(10);
    }
    assertTrue(condition.getAsBoolean(), "condition not reached in time");
  }

  @Test
  void cancellingARequestCancelsTheTransportExchange() throws InterruptedException {
    MockTransport backend = MockTransport.ofHanging(request -> true);
    CompletableFuture<?> future = client(backend, 0).run("r1").get();
    await(() -> backend.calls == 1);

    assertTrue(future.cancel(true));

    assertTrue(future.isCancelled());
    assertTrue(backend.allHungCancelled(), "the request in flight is aborted");
    assertThrows(CancellationException.class, future::join);
  }

  @Test
  void cancellingADerivedStageCancelsTheCallItComesFrom() throws InterruptedException {
    MockTransport backend = MockTransport.ofHanging(request -> true);
    CompletableFuture<String> derived =
        client(backend, 0)
            .run("r1")
            .get()
            .thenApply(run -> "x")
            .thenCompose(CompletableFuture::completedFuture);
    await(() -> backend.calls == 1);

    derived.cancel(true);

    assertTrue(backend.allHungCancelled());
  }

  @Test
  void cancellingDuringTheBackoffStopsTheRetries() throws InterruptedException {
    MockTransport backend = MockTransport.ofConstant(503, "{}");
    CompletableFuture<?> future = client(backend, 5).run("r1").get();
    await(() -> backend.calls == 1);

    future.cancel(true);
    Thread.sleep(900); // longer than the first backoff delay (300-600 ms)

    assertEquals(1, backend.calls, "a cancelled call is not retried");
  }

  @Test
  void cancellingAWaitForFinishStopsThePolling() throws InterruptedException {
    MockTransport backend = MockTransport.ofHanging(request -> false);
    // The run never finishes: every poll answers RUNNING at once.
    CompletableFuture<?> future = client(backend, 0).run("r1").waitForFinish(600L);
    await(() -> backend.calls >= 2);

    future.cancel(true);
    int callsAtCancel = backend.calls;
    Thread.sleep(600);

    assertTrue(backend.calls <= callsAtCancel + 1, "polling stops: " + backend.calls);
    assertTrue(future.isCancelled());
  }

  @Test
  void cancellingAHangingPollAbortsItsRequest() throws InterruptedException {
    MockTransport backend = MockTransport.ofHanging(request -> true);
    CompletableFuture<?> future = client(backend, 0).run("r1").waitForFinish(600L);
    await(() -> backend.calls == 1);

    future.cancel(true);

    assertTrue(backend.allHungCancelled(), "a stalled poll can be escaped by cancelling");
    Thread.sleep(300);
    assertEquals(1, backend.calls);
  }

  @Test
  void cancellingCallStopsItsPolling() throws InterruptedException {
    MockTransport backend = MockTransport.ofHanging(request -> request.method().equals("GET"));
    CompletableFuture<?> future =
        client(backend, 0).actor("a1").call(null, new ActorStartOptions(), null);
    await(() -> backend.hung.size() == 1);

    future.cancel(true);

    assertTrue(backend.allHungCancelled());
  }

  @Test
  void cancellingACollectedIterationCancelsThePageInFlight() throws InterruptedException {
    MockTransport backend = MockTransport.ofHanging(request -> true);
    CompletableFuture<?> future =
        Publishers.collect(client(backend, 0).actors().iterate(new ActorListOptions(), 10L));
    await(() -> backend.calls == 1);

    future.cancel(true);

    assertTrue(backend.allHungCancelled());
  }

  @Test
  void cancellingABatchAddCancelsTheChunksInFlight() throws InterruptedException {
    MockTransport backend = MockTransport.ofHanging(request -> true);
    List<RequestQueueRequest> requests =
        List.of(
            new RequestQueueRequest().setUrl("https://a.test").setUniqueKey("a"),
            new RequestQueueRequest().setUrl("https://b.test").setUniqueKey("b"));
    CompletableFuture<?> future =
        client(backend, 0).requestQueue("q1").batchAddRequests(requests, false);
    await(() -> backend.calls == 1);

    future.cancel(true);

    assertTrue(backend.allHungCancelled());
    assertFalse(future.isDone() && !future.isCancelled());
  }

  @Test
  void cancellingCallStopsTheLogRedirection() throws Exception {
    MockTransport backend =
        MockTransport.ofHanging(
            request ->
                request.uri().getQuery() != null
                    && request.uri().getQuery().contains("waitForFinish"));
    java.util.concurrent.atomic.AtomicBoolean streamClosed =
        new java.util.concurrent.atomic.AtomicBoolean();
    backend.scriptStream(
        200,
        new java.io.InputStream() {
          private final byte[] data =
              "2999-01-01T00:00:00.000Z hello\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
          private final java.util.concurrent.CountDownLatch closed =
              new java.util.concurrent.CountDownLatch(1);
          private int pos;

          // Serves one log line, then blocks like a live log with nothing more to say.
          @Override
          public synchronized int read(byte[] b, int off, int len) throws java.io.IOException {
            if (pos < data.length) {
              int n = Math.min(len, data.length - pos);
              System.arraycopy(data, pos, b, off, n);
              pos += n;
              return n;
            }
            try {
              closed.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            throw new java.io.IOException("stream closed");
          }

          @Override
          public int read() throws java.io.IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n == -1 ? -1 : one[0] & 0xff;
          }

          @Override
          public void close() {
            streamClosed.set(true);
            closed.countDown();
          }
        });
    CompletableFuture<?> future =
        client(backend, 0)
            .actor("a1")
            .call(null, new com.apify.client.actor.ActorCallOptions(), null);
    // The start, the run fetch for the log prefix, the poll (hung) and the log stream itself.
    await(() -> backend.hung.size() == 1 && backend.calls >= 4);

    future.cancel(true);
    await(streamClosed::get);
    assertTrue(backend.allHungCancelled());
  }

  @Test
  void pollsIgnoreAWithTimeoutOverrideAndKeepHoldingTheFullMinute() {
    MockTransport backend =
        MockTransport.ofConstant(200, "{\"data\":{\"id\":\"r1\",\"status\":\"SUCCEEDED\"}}");
    client(backend, 0)
        .withTimeout(com.apify.client.http.Timeout.ofSeconds(5))
        .run("r1")
        .waitForFinish(120L)
        .join();
    assertTrue(backend.lastUrl.contains("waitForFinish=60"), backend.lastUrl);
    assertEquals(java.util.Optional.empty(), backend.lastTimeout, "a poll has no timeout");
  }

  @Test
  void aLongPollLoopDoesNotKeepEarlierResponsesReachable() throws InterruptedException {
    // Each poll answers with a 1 MiB body; the loop is still pending after several polls. The
    // future the caller holds must not keep the earlier polls' responses (or parsed runs) alive.
    // The test needs the JVM to honour System.gc(); skip it where it does not
    // (-XX:+DisableExplicitGC).
    java.lang.ref.WeakReference<Object> control = new java.lang.ref.WeakReference<>(new Object());
    System.gc();
    org.junit.jupiter.api.Assumptions.assumeTrue(control.get() == null, "System.gc() is a no-op");

    java.util.List<java.lang.ref.WeakReference<byte[]>> bodies =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    String padding = "x".repeat(1 << 20);
    MockTransport backend =
        MockTransport.ofRouter(
            request -> {
              MockTransport.Scripted response =
                  MockTransport.ok(
                      200,
                      "{\"data\":{\"id\":\"r1\",\"status\":\"RUNNING\",\"statusMessage\":\""
                          + padding
                          + "\"}}");
              bodies.add(new java.lang.ref.WeakReference<>(response.body));
              return response;
            });
    CompletableFuture<?> pending = client(backend, 0).run("r1").waitForFinish(600L);
    await(() -> backend.calls >= 8);

    int collected = 0;
    for (int attempt = 0; attempt < 20 && collected < 4; attempt++) {
      System.gc();
      Thread.sleep(50);
      collected = 0;
      for (var reference : List.copyOf(bodies).subList(0, bodies.size() - 2)) {
        if (reference.get() == null) {
          collected++;
        }
      }
    }
    pending.cancel(true);
    assertTrue(
        collected >= 4, "earlier poll responses stay reachable: only " + collected + " freed");
  }

  @Test
  void cancellingCallWithACustomLogDestinationStillStopsThePolling() throws InterruptedException {
    MockTransport backend =
        MockTransport.ofHanging(
            request ->
                request.uri().getQuery() != null
                    && request.uri().getQuery().contains("waitForFinish"));
    backend.scriptStream(200, "2999-01-01T00:00:00.000Z hello\n");
    var options =
        new com.apify.client.actor.ActorCallOptions()
            .logOptions(new com.apify.client.log.StreamedLogOptions().toLog(line -> {}));
    CompletableFuture<?> future = client(backend, 0).actor("a1").call(null, options, null);
    await(() -> backend.hung.size() == 1);

    future.cancel(true);

    assertTrue(backend.allHungCancelled(), "the poll is aborted with a custom log destination");
  }

  @Test
  void aCancelBeforeTheLogStreamOpensLeavesNoReaderThread() throws InterruptedException {
    MockTransport backend =
        MockTransport.ofHanging(
            request ->
                request.uri().getQuery() != null
                    && request.uri().getQuery().contains("waitForFinish"));
    backend.scriptStream(200, "2999-01-01T00:00:00.000Z hello\n");
    CompletableFuture<Void> gate = new CompletableFuture<>();
    backend.gateStreams(gate); // the log stream does not open until the gate is released
    long readersBefore = readerThreads();
    CompletableFuture<?> future =
        client(backend, 0)
            .actor("a1")
            .call(null, new com.apify.client.actor.ActorCallOptions(), null);
    await(() -> backend.calls >= 3); // start, run fetch for the prefix, and the stream open request

    future.cancel(true);
    gate.complete(null); // the held-back response arrives for an exchange that was cancelled
    Thread.sleep(300);

    assertEquals(readersBefore, readerThreads(), "no log reader thread is left behind");
  }

  @Test
  void closingAStreamedLogWhileItsStreamOpensLaunchesNoReader() throws Exception {
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":{\"id\":\"r1\"}}");
    backend.scriptStream(200, "2999-01-01T00:00:00.000Z hello\n");
    CompletableFuture<Void> gate = new CompletableFuture<>();
    backend.gateStreams(gate);
    long readersBefore = readerThreads();
    var log =
        client(backend, 0)
            .run("r1")
            .getStreamedLog(new com.apify.client.log.StreamedLogOptions().toLog(line -> {}))
            .join();

    CompletableFuture<Void> started = log.start();
    log.close();
    gate.complete(null);
    Thread.sleep(300);

    assertEquals(readersBefore, readerThreads());
    assertTrue(started.isCancelled(), "the start that was closed is cancelled");
    log.start().join(); // a closed-while-opening log can be started again
    log.close();
  }

  @Test
  void aDestinationThatClosesTheLogCannotDeadlockTheStageChainedOnStart() throws Exception {
    MockTransport backend = MockTransport.ofConstant(200, "{\"data\":{\"id\":\"r1\"}}");
    backend.scriptStream(200, "2999-01-01T00:00:00.000Z hello\n");
    CompletableFuture<Void> gate = new CompletableFuture<>();
    backend.gateStreams(gate);
    CompletableFuture<Void> sawLine = new CompletableFuture<>();
    var holder =
        new java.util.concurrent.atomic.AtomicReference<com.apify.client.log.StreamedLog>();
    holder.set(
        client(backend, 0)
            .run("r1")
            .getStreamedLog(
                new com.apify.client.log.StreamedLogOptions()
                    .toLog(
                        line -> {
                          holder.get().close(); // allowed from the destination: takes the lock
                          sawLine.complete(null);
                        }))
            .join());

    // A stage chained on start() waits for the destination, which needs the StreamedLog lock: the
    // future must therefore be completed after the lock is released.
    CompletableFuture<Void> chained =
        holder
            .get()
            .start()
            .thenRun(() -> sawLine.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join());
    gate.complete(null);

    chained.get(10, java.util.concurrent.TimeUnit.SECONDS);
  }

  private static long readerThreads() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(thread -> thread.getName().equals("apify-streamed-log") && thread.isAlive())
        .count();
  }

  @Test
  void aCancelThatRacesAStreamResponseClosesItsBody() throws Exception {
    java.util.concurrent.atomic.AtomicBoolean closed =
        new java.util.concurrent.atomic.AtomicBoolean();
    java.io.InputStream body =
        new java.io.ByteArrayInputStream(new byte[0]) {
          @Override
          public void close() {
            closed.set(true);
          }
        };
    MockTransport mock = MockTransport.ofConstant(200, "");
    mock.scriptStream(200, body);
    java.net.http.HttpRequest request =
        java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://x/y")).build();
    java.net.http.HttpResponse<java.io.InputStream> response =
        mock.sendStreamingAsync(request).join();
    // An exchange that has already produced its response: cancelling it is a no-op, as in the JDK
    // client once the headers have arrived but before the callback has run.
    CompletableFuture<java.net.http.HttpResponse<java.io.InputStream>> exchange =
        new CompletableFuture<>() {
          @Override
          public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
          }
        };
    com.apify.client.http.HttpTransport transport =
        new com.apify.client.http.HttpTransport() {
          @Override
          public CompletableFuture<java.net.http.HttpResponse<byte[]>> sendAsync(
              java.net.http.HttpRequest r) {
            return mock.sendAsync(r);
          }

          @Override
          public CompletableFuture<java.net.http.HttpResponse<java.io.InputStream>>
              sendStreamingAsync(java.net.http.HttpRequest r) {
            return exchange;
          }
        };
    CompletableFuture<java.io.InputStream> stream = client(transport, 0).run("r1").log().stream();

    assertTrue(stream.cancel(true));
    exchange.complete(response);

    await(closed::get);
  }

  @Test
  void cancellingACopyDoesNotCancelTheOriginal() throws InterruptedException {
    MockTransport backend = MockTransport.ofHanging(request -> true);
    CompletableFuture<?> shared = client(backend, 0).run("r1").get();
    await(() -> backend.calls == 1);

    assertTrue(shared.copy().cancel(true));

    assertFalse(shared.isCancelled());
    assertFalse(backend.allHungCancelled(), "the request behind the original keeps running");
  }
}
