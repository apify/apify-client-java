package com.apify.client.http;

import com.apify.client.ApifyClientBuilder;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * The replaceable, non-blocking transport of the client.
 *
 * <p>An implementation sends one fully prepared request without blocking the calling thread and
 * completes the returned future with the raw response. The client handles authentication, the
 * {@code User-Agent} header, retries and (de)serialization.
 *
 * <p>A non-2xx status is not an error here: complete the future normally with the {@link
 * HttpResponse}. Only a transport-level failure (connection refused, DNS, timeout) completes it
 * exceptionally.
 *
 * <p>Swap the default implementation via {@link ApifyClientBuilder#httpTransport(HttpTransport)} to
 * share a connection pool, customize TLS/proxy settings, or inject a mock in tests.
 */
public interface HttpTransport {

  /**
   * Sends a request and buffers the whole response body as bytes, completing the returned future
   * once the exchange finishes. Completes exceptionally with an {@link HttpTimeoutException} on a
   * timeout; any other transport-level failure (connection refused, DNS, ...) completes it with
   * whatever unchecked {@code Throwable} the transport implementation fails with - for {@link
   * DefaultHttpTransport}, that is typically an {@code IOException} the JDK {@link
   * java.net.http.HttpClient} itself failed with, since this contract has no checked-exception
   * requirement of its own.
   */
  CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest request);

  /**
   * Sends a request and completes the returned future with the response body as a live {@link
   * InputStream}, for incremental consumption (used by log streaming). The caller closes the
   * stream. Completes exceptionally the same way as {@link #sendAsync(HttpRequest)}.
   */
  CompletableFuture<HttpResponse<InputStream>> sendStreamingAsync(HttpRequest request);

  /**
   * As {@link #sendAsync(HttpRequest)}, but a transport that is asked not to follow redirects
   * returns a redirect response as it is, since the request carries a token that must not follow a
   * redirect to another host. A request to an Actor's server is sent this way (see {@link
   * com.apify.client.actor.ActorServerClient#request}).
   *
   * <p>The default implementation sends the request as {@link #sendAsync(HttpRequest)} does when
   * {@code followRedirects} is {@code true}, and otherwise completes the returned future
   * exceptionally with an {@link UnsupportedOperationException}: a transport that cannot hold back
   * redirects must not be trusted with such a request. Override it to support them.
   *
   * @param followRedirects whether the transport may follow redirects
   */
  default CompletableFuture<HttpResponse<byte[]>> sendAsync(
      HttpRequest request, boolean followRedirects) {
    return followRedirects
        ? sendAsync(request)
        : CompletableFuture.failedFuture(redirectsCannotBeDisabled());
  }

  /**
   * As {@link #sendStreamingAsync(HttpRequest)}, with the redirect handling and the default
   * implementation of {@link #sendAsync(HttpRequest, boolean)}.
   *
   * @param followRedirects whether the transport may follow redirects
   */
  default CompletableFuture<HttpResponse<InputStream>> sendStreamingAsync(
      HttpRequest request, boolean followRedirects) {
    return followRedirects
        ? sendStreamingAsync(request)
        : CompletableFuture.failedFuture(redirectsCannotBeDisabled());
  }

  /** The failure of the default implementations when redirects must not be followed. */
  private static UnsupportedOperationException redirectsCannotBeDisabled() {
    return new UnsupportedOperationException(
        "This HttpTransport does not support disabling redirects, which a request to an Actor's"
            + " server needs so that the API token never follows a redirect to another host."
            + " Override sendAsync(HttpRequest, boolean) and sendStreamingAsync(HttpRequest,"
            + " boolean) to support it.");
  }
}
