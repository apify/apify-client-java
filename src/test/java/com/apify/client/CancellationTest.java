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

  private static ApifyClient client(MockTransport backend, int maxRetries) {
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
}
