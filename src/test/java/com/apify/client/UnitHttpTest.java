package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Offline unit tests for the retry/error/404 logic, using a mock HTTP backend. */
class UnitHttpTest {

  private static ApifyClient client(MockBackend backend, int maxRetries) {
    return ApifyClient.builder()
        .token("test-token")
        .httpBackend(backend)
        .maxRetries(maxRetries)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  @Test
  void successSingleCall() {
    MockBackend backend =
        MockBackend.ofConstant(200, "{\"data\":{\"id\":\"u1\",\"username\":\"bob\"}}");
    Optional<User> user = client(backend, 8).me().get();
    assertTrue(user.isPresent());
    assertEquals("u1", user.get().getId());
    assertEquals("bob", user.get().getUsername());
    assertEquals(1, backend.calls);
  }

  @Test
  void rateLimitIsRetried() {
    MockBackend backend =
        MockBackend.ofConstant(
            429, "{\"error\":{\"type\":\"rate-limit-exceeded\",\"message\":\"slow down\"}}");
    ApifyApiException ex =
        assertThrows(ApifyApiException.class, () -> client(backend, 2).me().get());
    assertEquals(429, ex.getStatusCode());
    assertEquals(3, backend.calls); // 1 initial + 2 retries
    assertEquals(3, ex.getAttempt());
  }

  @Test
  void serverErrorIsRetried() {
    MockBackend backend =
        MockBackend.ofConstant(503, "{\"error\":{\"type\":\"internal\",\"message\":\"boom\"}}");
    assertThrows(ApifyApiException.class, () -> client(backend, 1).me().get());
    assertEquals(2, backend.calls);
  }

  @Test
  void clientErrorNotRetried() {
    MockBackend backend =
        MockBackend.ofConstant(400, "{\"error\":{\"type\":\"bad-request\",\"message\":\"nope\"}}");
    assertThrows(ApifyApiException.class, () -> client(backend, 5).me().get());
    assertEquals(1, backend.calls);
  }

  @Test
  void networkErrorIsRetried() {
    MockBackend backend = new MockBackend(List.of(MockBackend.networkError()));
    assertThrows(RuntimeException.class, () -> client(backend, 3).me().get());
    assertEquals(4, backend.calls);
  }

  @Test
  void retryThenSuccess() {
    MockBackend backend =
        new MockBackend(
            List.of(
                MockBackend.ok(500, "{\"error\":{\"type\":\"internal\",\"message\":\"x\"}}"),
                MockBackend.ok(500, "{\"error\":{\"type\":\"internal\",\"message\":\"x\"}}"),
                MockBackend.ok(200, "{\"data\":{\"id\":\"ok\"}}")));
    Optional<User> user = client(backend, 5).me().get();
    assertTrue(user.isPresent());
    assertEquals("ok", user.get().getId());
    assertEquals(3, backend.calls);
  }

  @Test
  void notFoundMapsToEmpty() {
    MockBackend backend =
        MockBackend.ofConstant(
            404, "{\"error\":{\"type\":\"record-not-found\",\"message\":\"missing\"}}");
    Optional<Actor> actor = client(backend, 5).actor("nope").get();
    assertFalse(actor.isPresent());
    assertEquals(1, backend.calls); // no retry on 404
  }

  @Test
  void errorBodyIsParsed() {
    MockBackend backend =
        MockBackend.ofConstant(
            400,
            "{\"error\":{\"type\":\"bad-request\",\"message\":\"invalid input\",\"data\":{\"field\":\"name\"}}}");
    ApifyApiException ex =
        assertThrows(ApifyApiException.class, () -> client(backend, 0).me().get());
    assertEquals(400, ex.getStatusCode());
    assertEquals("bad-request", ex.getType());
    assertTrue(ex.getMessage().contains("invalid input"));
    assertEquals("GET", ex.getHttpMethod());
    assertFalse(ex.getPath().isEmpty());
    assertEquals("name", ex.getData().get("field"));
  }

  @Test
  void zeroRetriesSingleAttempt() {
    MockBackend backend =
        MockBackend.ofConstant(500, "{\"error\":{\"type\":\"internal\",\"message\":\"x\"}}");
    assertThrows(ApifyApiException.class, () -> client(backend, 0).me().get());
    assertEquals(1, backend.calls);
  }
}
