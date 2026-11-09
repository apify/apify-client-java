package com.apify.client.actor;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpHeaders;

/**
 * A response of an Actor's server whose body is still unread, as returned by {@link
 * ActorServerClient#requestStream}.
 *
 * <p>The body holds its connection open until it is read to the end or closed, so close the
 * response (it is {@link AutoCloseable}) or its {@link #body()} when done with it.
 *
 * @param statusCode the HTTP status code, of an error response too
 * @param headers the response headers
 * @param body the response body, unread
 */
public record ActorServerStreamResponse(int statusCode, HttpHeaders headers, InputStream body)
    implements AutoCloseable {

  /** Closes the body stream, releasing the connection. */
  @Override
  public void close() throws IOException {
    body.close();
  }
}
