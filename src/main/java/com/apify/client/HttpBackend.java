package com.apify.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * The replaceable transport contract of the client.
 *
 * <p>Implementations are responsible only for sending a single, fully-prepared request and
 * returning the raw response. Authentication, the {@code User-Agent} header, retries and
 * (de)serialization are handled by the client, so a backend only needs to perform one network
 * round-trip.
 *
 * <p>A non-2xx HTTP status is <b>not</b> an error at this layer — return it as a normal {@link
 * HttpResponse}. Only transport-level failures (connection refused, DNS, timeout) should be thrown.
 *
 * <p>Swap the default implementation via {@link ApifyClientBuilder#httpBackend(HttpBackend)} to
 * share a connection pool, customize TLS/proxy settings, or inject a mock in tests.
 */
public interface HttpBackend {

  /** Sends a single request and buffers the whole response body as bytes. */
  HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException;

  /**
   * Sends a single request and returns the response body as a live {@link InputStream}, for
   * incremental consumption (used by log streaming). The caller closes the stream.
   */
  HttpResponse<InputStream> sendStreaming(HttpRequest request)
      throws IOException, InterruptedException;
}
