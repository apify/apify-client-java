package com.apify.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The default {@link HttpBackend}, backed by the JDK's {@link java.net.http.HttpClient}.
 *
 * <p>The per-attempt timeout is applied to each {@link HttpRequest} by the orchestrating client, so
 * this backend sets only a connection timeout of its own. It follows normal redirects and reuses
 * connections from the underlying client's pool.
 */
public final class DefaultHttpBackend implements HttpBackend {

  /**
   * Connection-establishment timeout (distinct from the per-request timeout the client applies).
   */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

  private final HttpClient client;

  /** Creates a backend with a sensible default {@link java.net.http.HttpClient}. */
  public DefaultHttpBackend() {
    // Follow redirects (NORMAL, matching the reference clients): some endpoints — e.g. a
    // non-attachment key-value-store record GET — answer with a 302 to external storage, which the
    // JDK's default Redirect.NEVER would otherwise surface as an error. NORMAL does not follow an
    // HTTPS->HTTP downgrade and the JDK strips the Authorization header on cross-origin hops, so
    // the
    // bearer token is not leaked to the redirect target.
    this(
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
  }

  /** Wraps a caller-provided {@link java.net.http.HttpClient} (share a pool, custom proxy/TLS). */
  public DefaultHttpBackend(HttpClient client) {
    this.client = client;
  }

  @Override
  public HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException {
    return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
  }

  @Override
  public HttpResponse<InputStream> sendStreaming(HttpRequest request)
      throws IOException, InterruptedException {
    return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
  }
}
