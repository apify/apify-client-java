package com.apify.client;

import com.apify.client.http.HttpTimeoutException;
import com.apify.client.http.HttpTransport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLSession;

/**
 * A deterministic {@link HttpTransport} for offline unit tests. It serves a queue of scripted
 * responses/errors, records how many times it was called, and captures the last request's headers,
 * URL and body.
 */
final class MockTransport implements HttpTransport {

  /** One scripted response: an HTTP status + body, or a transport error. */
  static final class Scripted {
    final int status;
    final byte[] body;
    final IOException error;

    Scripted(int status, String body, IOException error) {
      this.status = status;
      this.body = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
      this.error = error;
    }
  }

  private final List<Scripted> responses;
  int calls = 0;
  HttpHeaders lastHeaders;
  String lastUrl;
  String lastMethod;
  String lastBody;
  byte[] lastBodyBytes;
  final List<String> bodies = new ArrayList<>();

  /**
   * Optional scripted response for {@link #sendStreamingResponse}; defaults to the first {@code
   * send} entry.
   */
  private Scripted streamResponse;

  /** Optional custom stream body (e.g. a blocking stream that simulates a live, open log). */
  private InputStream streamBody;

  private int streamBodyStatus;

  MockTransport(List<Scripted> responses) {
    this.responses = responses;
  }

  static MockTransport ofConstant(int status, String body) {
    return new MockTransport(List.of(new Scripted(status, body, null)));
  }

  static Scripted ok(int status, String body) {
    return new Scripted(status, body, null);
  }

  static Scripted networkError() {
    return new Scripted(0, null, new IOException("connection refused"));
  }

  static Scripted timeoutError() {
    // Throws the HttpTransport contract's own transport-implementation-agnostic timeout signal
    // (not the JDK HttpClient's), matching what any real transport implementation is expected to
    // do.
    return new Scripted(0, null, new HttpTimeoutException("request timed out", null));
  }

  // synchronized: batchAddRequests may drive this transport from several threads at once.
  @Override
  public synchronized HttpResponse<byte[]> send(HttpRequest request) throws IOException {
    int idx = calls++;
    lastHeaders = request.headers();
    lastUrl = request.uri().toString();
    lastMethod = request.method();
    lastBodyBytes = readBody(request);
    lastBody = lastBodyBytes == null ? null : new String(lastBodyBytes, StandardCharsets.UTF_8);
    if (lastBody != null) {
      bodies.add(lastBody);
    }
    if (idx >= responses.size()) {
      idx = responses.size() - 1; // repeat the last entry
    }
    Scripted r = responses.get(idx);
    if (r.error != null) {
      throw r.error;
    }
    return new FakeResponse(request.uri(), r.status, r.body);
  }

  /** Scripts the next {@link #sendStreamingResponse} call to return the given status and body. */
  void scriptStream(int status, String body) {
    this.streamResponse = new Scripted(status, body, null);
    this.streamBody = null;
  }

  /**
   * Scripts the next {@link #sendStreamingResponse} call to return the given status and an
   * arbitrary, possibly blocking, {@link InputStream}. Unlike {@link #scriptStream(int, String)},
   * the body is not a finite in-memory buffer, so {@code read()} does not naturally reach
   * end-of-stream; this models a live log that only ends when the stream is closed.
   */
  void scriptStream(int status, InputStream body) {
    this.streamBodyStatus = status;
    this.streamBody = body;
    this.streamResponse = null;
  }

  @Override
  public synchronized HttpResponse<InputStream> sendStreamingResponse(HttpRequest request) {
    calls++;
    lastUrl = request.uri().toString();
    lastMethod = request.method();
    if (streamBody != null) {
      return new FakeStreamResponse(request.uri(), streamBodyStatus, streamBody);
    }
    Scripted r = streamResponse != null ? streamResponse : responses.get(0);
    return new FakeStreamResponse(
        request.uri(), r.status, new java.io.ByteArrayInputStream(r.body));
  }

  private static byte[] readBody(HttpRequest request) {
    Optional<HttpRequest.BodyPublisher> publisher = request.bodyPublisher();
    if (publisher.isEmpty() || publisher.get().contentLength() == 0) {
      return null;
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    CountDownLatch latch = new CountDownLatch(1);
    publisher
        .get()
        .subscribe(
            new Flow.Subscriber<>() {
              @Override
              public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
              }

              @Override
              public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                out.writeBytes(chunk);
              }

              @Override
              public void onError(Throwable throwable) {
                latch.countDown();
              }

              @Override
              public void onComplete() {
                latch.countDown();
              }
            });
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return out.toByteArray();
  }

  /** Minimal {@link HttpResponse} implementation over a byte[] body. */
  private static final class FakeResponse implements HttpResponse<byte[]> {
    private final URI uri;
    private final int status;
    private final byte[] body;

    FakeResponse(URI uri, int status, byte[] body) {
      this.uri = uri;
      this.status = status;
      this.body = body;
    }

    @Override
    public int statusCode() {
      return status;
    }

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<byte[]>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (a, b) -> true);
    }

    @Override
    public byte[] body() {
      return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return uri;
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }

  /** Minimal {@link HttpResponse} over an {@link InputStream} body, for streaming tests. */
  private static final class FakeStreamResponse implements HttpResponse<InputStream> {
    private final URI uri;
    private final int status;
    private final InputStream body;

    FakeStreamResponse(URI uri, int status, InputStream body) {
      this.uri = uri;
      this.status = status;
      this.body = body;
    }

    @Override
    public int statusCode() {
      return status;
    }

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<InputStream>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (a, b) -> true);
    }

    @Override
    public InputStream body() {
      return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return uri;
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }
}
