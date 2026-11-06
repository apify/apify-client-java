package com.apify.client.http;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The default {@link HttpTransport}, backed by the JDK's {@link java.net.http.HttpClient} and its
 * native non-blocking {@code sendAsync}.
 *
 * <p>The per-attempt timeout is applied to each {@link HttpRequest} by the orchestrating client, so
 * this transport sets only a connection timeout of its own. It follows normal redirects, except for
 * a request that asks not to (see {@link HttpTransport#sendAsync(HttpRequest, boolean)}), and
 * reuses connections from the underlying client's pool.
 */
public final class DefaultHttpTransport implements HttpTransport {

  /**
   * Default connection-establishment timeout (distinct from the per-request timeout the client
   * applies), used when no explicit value is given to {@link #DefaultHttpTransport(Duration)}.
   * Generous enough for a slow TLS handshake over a high-latency link without leaving a hung DNS
   * lookup or a filtered port stuck for the full per-request timeout.
   */
  public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private final HttpClient client;

  /**
   * The client that serves a request which must not follow redirects: {@link #client} itself when
   * it never follows them, otherwise a copy of it that does not. Created on first use.
   */
  private volatile HttpClient noRedirectClient;

  /** Creates a transport with the {@link #DEFAULT_CONNECT_TIMEOUT}. */
  public DefaultHttpTransport() {
    this(DEFAULT_CONNECT_TIMEOUT);
  }

  /** Creates a transport with an explicit connection-establishment timeout. */
  public DefaultHttpTransport(Duration connectTimeout) {
    // Follow redirects (NORMAL, matching the reference clients): some endpoints — e.g. a
    // non-attachment key-value-store record GET — answer with a 302 to external storage, which the
    // JDK's default Redirect.NEVER would otherwise surface as an error. NORMAL does not follow an
    // HTTPS->HTTP downgrade, and the JDK strips the Authorization header on cross-origin hops, so
    // the bearer token is not leaked to the redirect target.
    this(
        HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
  }

  /** Wraps a caller-provided {@link java.net.http.HttpClient} (share a pool, custom proxy/TLS). */
  public DefaultHttpTransport(HttpClient client) {
    this.client = client;
  }

  @Override
  public CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest request) {
    return unwrapTimeout(client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()));
  }

  @Override
  public CompletableFuture<HttpResponse<InputStream>> sendStreamingAsync(HttpRequest request) {
    return unwrapTimeout(client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()));
  }

  @Override
  public CompletableFuture<HttpResponse<byte[]>> sendAsync(
      HttpRequest request, boolean followRedirects) {
    return followRedirects
        ? sendAsync(request)
        : unwrapTimeout(
            clientWithoutRedirects().sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()));
  }

  @Override
  public CompletableFuture<HttpResponse<InputStream>> sendStreamingAsync(
      HttpRequest request, boolean followRedirects) {
    return followRedirects
        ? sendStreamingAsync(request)
        : unwrapTimeout(
            clientWithoutRedirects().sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()));
  }

  /**
   * The JDK client's redirect policy is fixed when it is built, so a request that must not follow
   * redirects goes through a client that was built without that policy. A client wrapped by {@link
   * #DefaultHttpTransport(HttpClient)} is copied with the settings the JDK exposes (it does not
   * share the original's connection pool).
   */
  private HttpClient clientWithoutRedirects() {
    HttpClient result = noRedirectClient;
    if (result == null) {
      synchronized (this) {
        result = noRedirectClient;
        if (result == null) {
          result = copyWithoutRedirects(client);
          noRedirectClient = result;
        }
      }
    }
    return result;
  }

  private static HttpClient copyWithoutRedirects(HttpClient source) {
    if (source.followRedirects() == HttpClient.Redirect.NEVER) {
      return source;
    }
    HttpClient.Builder builder =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(source.version())
            .sslContext(source.sslContext())
            .sslParameters(source.sslParameters());
    source.connectTimeout().ifPresent(builder::connectTimeout);
    source.proxy().ifPresent(builder::proxy);
    source.authenticator().ifPresent(builder::authenticator);
    source.cookieHandler().ifPresent(builder::cookieHandler);
    source.executor().ifPresent(builder::executor);
    return builder.build();
  }

  /**
   * Rewrites a {@link java.net.http.HttpTimeoutException} the JDK client fails the future with into
   * this contract's transport-agnostic {@link HttpTimeoutException}, unwrapping the {@link
   * CompletionException} {@code sendAsync} wraps it in.
   */
  private static <T> CompletableFuture<T> unwrapTimeout(CompletableFuture<T> future) {
    CompletableFuture<T> result = new CompletableFuture<>();
    // Cancelling the returned future aborts the exchange the JDK client has in flight.
    result.whenComplete(
        (value, error) -> {
          if (result.isCancelled()) {
            future.cancel(true);
          }
        });
    future.whenComplete(
        (value, error) -> {
          if (error == null) {
            result.complete(value);
            return;
          }
          Throwable cause = error instanceof CompletionException ? error.getCause() : error;
          if (cause instanceof java.net.http.HttpTimeoutException timeout) {
            result.completeExceptionally(new HttpTimeoutException(timeout.getMessage(), timeout));
          } else {
            result.completeExceptionally(cause);
          }
        });
    return result;
  }
}
