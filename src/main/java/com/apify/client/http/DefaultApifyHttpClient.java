package com.apify.client.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The default {@link HttpClient}, backed by the JDK's {@link java.net.http.HttpClient}.
 *
 * <p>The per-attempt timeout is applied to each {@link HttpRequest} by the orchestrating client, so
 * this backend sets only a connection timeout of its own. It follows normal redirects and reuses
 * connections from the underlying client's pool.
 *
 * <p>The JDK's own {@code java.net.http.HttpClient} is always referenced by its fully-qualified
 * name in this class (never imported unqualified): an unqualified single-type import would clash
 * with this package's own {@link HttpClient} interface of the same simple name.
 */
public final class DefaultApifyHttpClient implements HttpClient {

  /**
   * Connection-establishment timeout (distinct from the per-request timeout the client applies,
   * which bounds an entire request/response round-trip and can reasonably be long for large
   * dataset/key-value-store payloads). 10 seconds is generous for establishing a TCP+TLS connection
   * to the Apify API even over a slow network, while still failing well before the per-request
   * timeout on a genuinely unreachable host. To use a different value (or no separate connect
   * timeout at all), configure your own {@link java.net.http.HttpClient} and pass it to {@link
   * #DefaultApifyHttpClient(java.net.http.HttpClient)}.
   */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private final java.net.http.HttpClient client;

  /** Creates a backend with a sensible default {@link java.net.http.HttpClient}. */
  public DefaultApifyHttpClient() {
    // Follow redirects (NORMAL, matching the reference clients): some endpoints — e.g. a
    // non-attachment key-value-store record GET — answer with a 302 to external storage, which the
    // JDK's default Redirect.NEVER would otherwise surface as an error. NORMAL does not follow an
    // HTTPS->HTTP downgrade and the JDK strips the Authorization header on cross-origin hops, so
    // the
    // bearer token is not leaked to the redirect target.
    this(
        java.net.http.HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .build());
  }

  /** Wraps a caller-provided {@link java.net.http.HttpClient} (share a pool, custom proxy/TLS). */
  public DefaultApifyHttpClient(java.net.http.HttpClient client) {
    this.client = client;
  }

  @Override
  public HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException {
    return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
  }

  @Override
  public HttpResponse<InputStream> sendStreamingResponse(HttpRequest request)
      throws IOException, InterruptedException {
    return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
  }
}
