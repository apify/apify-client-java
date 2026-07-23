package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.actor.Actor;
import com.apify.client.http.ApifyApiException;
import com.apify.client.user.User;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Offline unit tests for the retry/error/404 logic, using a mock HTTP transport. */
class UnitHttpTest {

  private static ApifyClient client(MockTransport transport, int maxRetries) {
    return ApifyClient.builder()
        .token("test-token")
        .httpTransport(transport)
        .maxRetries(maxRetries)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  @Test
  void successSingleCall() {
    MockTransport transport =
        MockTransport.ofConstant(200, "{\"data\":{\"id\":\"u1\",\"username\":\"bob\"}}");
    Optional<User> user = client(transport, 8).me().get();
    assertTrue(user.isPresent());
    assertEquals("u1", user.get().getId());
    assertEquals("bob", user.get().getUsername());
    assertEquals(1, transport.calls);
  }

  @Test
  void rateLimitIsRetried() {
    MockTransport transport =
        MockTransport.ofConstant(
            429, "{\"error\":{\"type\":\"rate-limit-exceeded\",\"message\":\"slow down\"}}");
    ApifyApiException ex =
        assertThrows(ApifyApiException.class, () -> client(transport, 2).me().get());
    assertEquals(429, ex.getStatusCode());
    assertEquals(3, transport.calls); // 1 initial + 2 retries
    assertEquals(3, ex.getAttempt());
  }

  @Test
  void serverErrorIsRetried() {
    MockTransport transport =
        MockTransport.ofConstant(503, "{\"error\":{\"type\":\"internal\",\"message\":\"boom\"}}");
    assertThrows(ApifyApiException.class, () -> client(transport, 1).me().get());
    assertEquals(2, transport.calls);
  }

  @Test
  void clientErrorNotRetried() {
    MockTransport transport =
        MockTransport.ofConstant(
            400, "{\"error\":{\"type\":\"bad-request\",\"message\":\"nope\"}}");
    assertThrows(ApifyApiException.class, () -> client(transport, 5).me().get());
    assertEquals(1, transport.calls);
  }

  @Test
  void networkErrorIsRetried() {
    MockTransport transport = new MockTransport(List.of(MockTransport.networkError()));
    assertThrows(RuntimeException.class, () -> client(transport, 3).me().get());
    assertEquals(4, transport.calls);
  }

  @Test
  void retryThenSuccess() {
    MockTransport transport =
        new MockTransport(
            List.of(
                MockTransport.ok(500, "{\"error\":{\"type\":\"internal\",\"message\":\"x\"}}"),
                MockTransport.ok(500, "{\"error\":{\"type\":\"internal\",\"message\":\"x\"}}"),
                MockTransport.ok(200, "{\"data\":{\"id\":\"ok\"}}")));
    Optional<User> user = client(transport, 5).me().get();
    assertTrue(user.isPresent());
    assertEquals("ok", user.get().getId());
    assertEquals(3, transport.calls);
  }

  @Test
  void notFoundMapsToEmpty() {
    MockTransport transport =
        MockTransport.ofConstant(
            404, "{\"error\":{\"type\":\"record-not-found\",\"message\":\"missing\"}}");
    Optional<Actor> actor = client(transport, 5).actor("nope").get();
    assertFalse(actor.isPresent());
    assertEquals(1, transport.calls); // no retry on 404
  }

  @Test
  void notFoundWithTypeButNoMessageMapsToEmpty() {
    // The `type` field alone must drive not-found detection; `message` may be absent.
    MockTransport transport =
        MockTransport.ofConstant(404, "{\"error\":{\"type\":\"record-not-found\"}}");
    Optional<Actor> actor = client(transport, 5).actor("nope").get();
    assertFalse(actor.isPresent());
    assertEquals(1, transport.calls); // no retry on 404
  }

  @Test
  void deleteOnNotFoundWithTypeButNoMessageIsNoOp() {
    MockTransport transport =
        MockTransport.ofConstant(404, "{\"error\":{\"type\":\"record-or-token-not-found\"}}");
    client(transport, 5).actor("nope").delete(); // must not throw
    assertEquals(1, transport.calls);
  }

  @Test
  void nonNotFoundErrorWithTypeButNoMessageStillThrowsWithType() {
    MockTransport transport =
        MockTransport.ofConstant(400, "{\"error\":{\"type\":\"bad-request\"}}");
    ApifyApiException ex =
        assertThrows(ApifyApiException.class, () -> client(transport, 0).me().get());
    assertEquals(400, ex.getStatusCode());
    assertEquals("bad-request", ex.getType());
  }

  @Test
  void errorBodyIsParsed() {
    MockTransport transport =
        MockTransport.ofConstant(
            400,
            "{\"error\":{\"type\":\"bad-request\",\"message\":\"invalid input\",\"data\":{\"field\":\"name\"}}}");
    ApifyApiException ex =
        assertThrows(ApifyApiException.class, () -> client(transport, 0).me().get());
    assertEquals(400, ex.getStatusCode());
    assertEquals("bad-request", ex.getType());
    assertTrue(ex.getMessage().contains("invalid input"));
    assertEquals("GET", ex.getHttpMethod());
    assertFalse(ex.getPath().isEmpty());
    assertEquals("name", ex.getData().get("field"));
  }

  @Test
  void zeroRetriesSingleAttempt() {
    MockTransport transport =
        MockTransport.ofConstant(500, "{\"error\":{\"type\":\"internal\",\"message\":\"x\"}}");
    assertThrows(ApifyApiException.class, () -> client(transport, 0).me().get());
    assertEquals(1, transport.calls);
  }
}
