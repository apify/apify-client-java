package com.apify.client.http;

import com.apify.client.ApifyClientBuilder;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * The replaceable, non-blocking transport contract of the client.
 *
 * <p>Implementations are responsible only for sending a single, fully-prepared request and
 * completing the returned future with the raw response — asynchronously, never blocking the calling
 * thread on the network round-trip. Authentication, the {@code User-Agent} header, retries and
 * (de)serialization are handled by the client, so a transport implementation only needs to perform
 * one non-blocking network round-trip.
 *
 * <p>A non-2xx HTTP status is <b>not</b> an error at this layer — complete the future normally with
 * that {@link HttpResponse}. Only a transport-level failure (connection refused, DNS, timeout)
 * should complete the future exceptionally.
 *
 * <p>Swap the default implementation via {@link ApifyClientBuilder#httpTransport(HttpTransport)} to
 * share a connection pool, customize TLS/proxy settings, or inject a mock in tests.
 */
public interface HttpTransport {

  /**
   * Sends a request and buffers the whole response body as bytes, completing the returned future
   * once the exchange finishes. Completes exceptionally with an {@link HttpTimeoutException} on a
   * timeout, or another {@link java.io.IOException} for any other transport-level failure.
   */
  CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest request);

  /**
   * Sends a request and completes the returned future with the response body as a live {@link
   * InputStream}, for incremental consumption (used by log streaming). The caller closes the
   * stream. Completes exceptionally the same way as {@link #sendAsync(HttpRequest)}.
   */
  CompletableFuture<HttpResponse<InputStream>> sendStreamingAsync(HttpRequest request);
}
