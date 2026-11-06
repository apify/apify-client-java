package com.apify.client.internal;

import com.apify.client.http.HttpCompressor;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * How {@link HttpClientCore} treats a single request, where it differs from a call to the Apify
 * API: which header carries the token, whether an error status throws, which statuses and transport
 * errors are retried, whether redirects are followed and how the body is compressed. Internal to
 * the client.
 *
 * @param authorizationHeader the name of the header that carries the client's token
 * @param throwOnErrorStatus whether an error status that is not retried any more is thrown as an
 *     {@link com.apify.client.http.ApifyApiException}; when {@code false} it is returned as the
 *     response instead
 * @param retryableStatus tells which error statuses are retried, or {@code null} for rate limits
 *     (429) and server errors (5xx)
 * @param retryTransportErrors whether a transport error, a timeout included, is retried
 * @param followRedirects whether the transport may follow redirects
 * @param compress whether the request body is compressed at all
 * @param compressor the compressor for this request, or {@code null} for the client's own; only
 *     used when {@code compress} is {@code true}
 */
public record RequestPolicy(
    String authorizationHeader,
    boolean throwOnErrorStatus,
    IntPredicate retryableStatus,
    boolean retryTransportErrors,
    boolean followRedirects,
    boolean compress,
    HttpCompressor compressor) {

  /**
   * Statuses a request to an Actor's server is retried on. The platform answers with them when it
   * cannot pass a request to a server run, so a retry usually does not run the Actor's code again.
   * An Actor that returns one itself gets the request again.
   */
  private static final Set<Integer> SERVER_RETRY_STATUS_CODES = Set.of(429, 503);

  /** A call to the Apify API. */
  public static final RequestPolicy API =
      new RequestPolicy(
          /* authorizationHeader= */ HttpHeaders.AUTHORIZATION,
          /* throwOnErrorStatus= */ true,
          /* retryableStatus= */ null,
          /* retryTransportErrors= */ true,
          /* followRedirects= */ true,
          /* compress= */ true,
          /* compressor= */ null);

  /**
   * A request to an Actor's server. The token travels in the {@code X-Apify-Authorization} header,
   * which the platform removes before the request reaches the Actor, so the {@code Authorization}
   * header stays free for the Actor's own authentication. Every response is returned, error
   * statuses included. Only the statuses in {@link #SERVER_RETRY_STATUS_CODES} are retried, and no
   * transport error is, since the request may have reached the Actor before it failed. Redirects
   * are not followed, since that would send the token to a host the Actor's code picked. The body
   * is sent uncompressed unless a {@code compressor} is given, since the Actor's server may not
   * decode a compressed body.
   *
   * @param compressor compresses the request body, or {@code null} to send it uncompressed
   */
  public static RequestPolicy server(HttpCompressor compressor) {
    return new RequestPolicy(
        /* authorizationHeader= */ HttpHeaders.X_APIFY_AUTHORIZATION,
        /* throwOnErrorStatus= */ false,
        /* retryableStatus= */ SERVER_RETRY_STATUS_CODES::contains,
        /* retryTransportErrors= */ false,
        /* followRedirects= */ false,
        /* compress= */ compressor != null,
        /* compressor= */ compressor);
  }
}
