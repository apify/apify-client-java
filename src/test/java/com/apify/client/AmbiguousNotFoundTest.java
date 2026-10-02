package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.http.NotFoundError;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/**
 * Hermetic tests for the ambiguous-404 rule (mirrors <a
 * href="https://github.com/apify/apify-client-js/pull/1042">apify-client-js#1042</a>): a resource
 * client addressed by an explicit id swallows a 404 to an empty {@code Optional}/no-op, but a
 * client reached through a run/build with no id of its own throws {@link NotFoundError} instead,
 * since the 404 could mean either the parent or the sub-resource is gone.
 */
class AmbiguousNotFoundTest {

  private static ApifyClient client(int status) {
    return ApifyClient.builder()
        .token("test-token")
        .httpTransport(MockTransport.ofConstant(status, notFoundBody()))
        .maxRetries(0)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  private static String notFoundBody() {
    return "{\"error\":{\"type\":\"record-not-found\",\"message\":\"not found\"}}";
  }

  // ---- Addressed by an explicit id: swallow to empty/no-op -------------------------------

  @Test
  void datasetByIdSwallows404ToEmpty() {
    assertTrue(client(404).dataset("missing").get().join().isEmpty());
  }

  @Test
  void keyValueStoreByIdSwallows404ToEmpty() {
    assertTrue(client(404).keyValueStore("missing").get().join().isEmpty());
  }

  @Test
  void requestQueueByIdSwallows404ToEmpty() {
    assertTrue(client(404).requestQueue("missing").get().join().isEmpty());
  }

  @Test
  void datasetByIdDeleteIsNoOpOn404() {
    client(404).dataset("missing").delete().join(); // must not throw
  }

  @Test
  void logByIdSwallows404ToEmpty() {
    assertTrue(client(404).log("missing").get().join().isEmpty());
  }

  // ---- Reached through a run/build with no id of its own: throw -------------------------

  @Test
  void runDatasetThrowsNotFoundError() {
    CompletionException e =
        assertThrows(CompletionException.class, () -> client(404).run("r1").dataset().get().join());
    assertInstanceOf(NotFoundError.class, e.getCause());
  }

  @Test
  void runKeyValueStoreThrowsNotFoundError() {
    CompletionException e =
        assertThrows(
            CompletionException.class, () -> client(404).run("r1").keyValueStore().get().join());
    assertInstanceOf(NotFoundError.class, e.getCause());
  }

  @Test
  void runRequestQueueThrowsNotFoundError() {
    CompletionException e =
        assertThrows(
            CompletionException.class, () -> client(404).run("r1").requestQueue().get().join());
    assertInstanceOf(NotFoundError.class, e.getCause());
  }

  @Test
  void runDatasetDeleteThrowsNotFoundError() {
    CompletionException e =
        assertThrows(
            CompletionException.class, () -> client(404).run("r1").dataset().delete().join());
    assertInstanceOf(NotFoundError.class, e.getCause());
  }

  @Test
  void runLogThrowsNotFoundError() {
    CompletionException e =
        assertThrows(CompletionException.class, () -> client(404).run("r1").log().get().join());
    assertInstanceOf(NotFoundError.class, e.getCause());
  }

  @Test
  void buildLogThrowsNotFoundError() {
    CompletionException e =
        assertThrows(CompletionException.class, () -> client(404).build("b1").log().get().join());
    assertInstanceOf(NotFoundError.class, e.getCause());
  }

  // ---- Non-404 errors must still propagate from the id-addressed ("swallow") path too ----

  @Test
  void datasetByIdStillThrowsOnNon404Error() {
    CompletionException e =
        assertThrows(CompletionException.class, () -> client(500).dataset("d1").get().join());
    assertFalse(e.getCause() instanceof NotFoundError);
  }
}
