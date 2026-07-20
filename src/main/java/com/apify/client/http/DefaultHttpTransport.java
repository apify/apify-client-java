package com.apify.client.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The default {@link HttpTransport}, backed by the JDK's {@link java.net.http.HttpClient}.
 *
 * <p>The per-attempt timeout is applied to each {@link HttpRequest} by the orchestrating client, so
 * this transport sets only a connection timeout of its own. It follows normal redirects and reuses
 * connections from the underlying client's pool.
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
  public HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException {
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (java.net.http.HttpTimeoutException e) {
      throw new HttpTimeoutException(e.getMessage(), e);
    }
  }

  @Override
  public HttpResponse<InputStream> sendStreamingResponse(HttpRequest request)
      throws IOException, InterruptedException {
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (java.net.http.HttpTimeoutException e) {
      throw new HttpTimeoutException(e.getMessage(), e);
    }
  }
}
