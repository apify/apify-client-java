package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apify.client.http.ApifyApiException;
import com.apify.client.http.ApifyClientException;
import com.apify.client.http.ApifyTransportException;
import com.apify.client.http.ConflictError;
import com.apify.client.http.ForbiddenError;
import com.apify.client.http.InvalidRequestError;
import com.apify.client.http.NotFoundError;
import com.apify.client.http.RateLimitError;
import com.apify.client.http.ServerError;
import com.apify.client.http.UnauthorizedError;
import com.apify.client.internal.HttpClientCore;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/**
 * Guards the exception hierarchy's headline contract: {@code catch (ApifyClientException)} must
 * catch every failure this client throws, both the "API responded with an error" case ({@link
 * ApifyApiException}) and the "no response was produced at all" case ({@link
 * ApifyTransportException}). A regression here (e.g. one subtype quietly stops extending the common
 * base) would silently break every caller relying on the documented single catch clause.
 */
class ExceptionHierarchyTest {

  @Test
  void apiExceptionIsCatchableAsClientException() {
    ApifyClientException caught =
        assertThrows(
            ApifyClientException.class,
            () -> {
              throw new ApifyApiException(
                  404, "record-not-found", "not found", 1, "GET", "/x", Map.of());
            });
    assertInstanceOf(ApifyApiException.class, caught);
  }

  @Test
  void transportExceptionIsCatchableAsClientException() {
    ApifyClientException caught =
        assertThrows(
            ApifyClientException.class,
            () -> {
              throw new ApifyTransportException(new IOException("connection refused"));
            });
    assertInstanceOf(ApifyTransportException.class, caught);
  }

  /**
   * {@link HttpClientCore#buildApiError} must construct the subclass matching each status code, so
   * callers can branch with {@code instanceof} instead of comparing {@code getStatusCode()} by
   * number (see the reference JS client's per-status {@code ApifyApiError} subclasses).
   */
  @Test
  void buildApiErrorPicksSubclassByStatus() {
    assertInstanceOf(InvalidRequestError.class, apiError(400));
    assertInstanceOf(UnauthorizedError.class, apiError(401));
    assertInstanceOf(ForbiddenError.class, apiError(403));
    assertInstanceOf(NotFoundError.class, apiError(404));
    assertInstanceOf(ConflictError.class, apiError(409));
    assertInstanceOf(RateLimitError.class, apiError(429));
    assertInstanceOf(ServerError.class, apiError(500));
    assertInstanceOf(ServerError.class, apiError(503));
    // A status with no dedicated subclass falls back to the plain base class (and nothing more
    // specific - the exact type matters here, not just assignability).
    ApifyApiException teapot = apiError(418);
    assertEquals(ApifyApiException.class, teapot.getClass());
  }

  @Test
  void everySubclassExtendsApifyApiException() {
    // Every subclass must stay catchable by an existing `catch (ApifyApiException e)`.
    for (ApifyApiException e :
        List.of(
            apiError(400),
            apiError(401),
            apiError(403),
            apiError(404),
            apiError(409),
            apiError(429),
            apiError(500))) {
      assertInstanceOf(ApifyApiException.class, e);
      assertInstanceOf(ApifyClientException.class, e);
    }
  }

  /**
   * End-to-end: a 404 API response surfaces through the real HTTP pipeline (not just the factory in
   * isolation) as a {@link NotFoundError}.
   */
  @Test
  void realRequestSurfacesTypedSubclass() {
    MockTransport backend =
        MockTransport.ofConstant(
            404, "{\"error\":{\"type\":\"record-not-found\",\"message\":\"not found\"}}");
    ApifyClient client =
        ApifyClient.builder()
            .token("test-token")
            .httpTransport(backend)
            .maxRetries(0)
            .minDelayBetweenRetries(Duration.ofMillis(1))
            .build();
    CompletionException wrapper =
        assertThrows(CompletionException.class, () -> client.task("nope").getInput().join());
    assertInstanceOf(NotFoundError.class, wrapper.getCause());
  }

  private static ApifyApiException apiError(int status) {
    return HttpClientCore.buildApiError(status, new byte[0], 1, "GET", "/x");
  }
}
