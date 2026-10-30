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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLSession;

/**
 * A deterministic {@link HttpTransport} for offline unit tests. It serves a queue of scripted
 * responses/errors, records how many times it was called, and captures the last request's headers,
 * URL and body.
 */
final class MockTransport implements HttpTransport {

  /** One scripted response: an HTTP status + body (+ optional headers), or a transport error. */
  static final class Scripted {
    final int status;
    final byte[] body;
    final Exception error;
    final Map<String, String> headers;

    Scripted(int status, String body, Exception error) {
      this(status, body, error, Map.of());
    }

    Scripted(int status, String body, Exception error, Map<String, String> headers) {
      this.status = status;
      this.body = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
      this.error = error;
      this.headers = headers;
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
  final List<String> urls = new ArrayList<>();

  /** The per-attempt timeout of the last request, empty when the request had none. */
  Optional<java.time.Duration> lastTimeout = Optional.empty();

  /** The per-attempt timeout of every request, in order. */
  final List<Optional<java.time.Duration>> timeouts = new ArrayList<>();

  /**
   * Requests this predicate accepts are never answered; their futures are kept in {@link #hung}.
   */
  private java.util.function.Predicate<HttpRequest> hangWhen = request -> false;

  /** The futures of the requests that were left unanswered. */
  final List<CompletableFuture<HttpResponse<byte[]>>> hung =
      new java.util.concurrent.CopyOnWriteArrayList<>();

  /** When set, streaming responses are delivered only once this gate completes. */
  private volatile CompletableFuture<Void> streamGate;

  /** Holds back every streaming response until {@code gate} completes. */
  void gateStreams(CompletableFuture<Void> gate) {
    this.streamGate = gate;
  }

  /** When set, answers each request itself instead of serving the scripted queue. */
  private java.util.function.Function<HttpRequest, Scripted> router;

  /** One scripted response for {@link #sendStreamingAsync}: a status + ready-made body stream. */
  private static final class StreamEntry {
    final int status;
    final InputStream body;

    StreamEntry(int status, InputStream body) {
      this.status = status;
      this.body = body;
    }
  }

  /**
   * Queue of scripted {@link #sendStreamingAsync} responses, consumed one per call (the last entry
   * repeats once exhausted, like {@link #responses}). Lets a test script a sequence - e.g. an empty
   * stream followed by one with content, to exercise {@code StreamedLog}'s reopen-on-empty-stream
   * behavior - instead of only ever the same single response.
   */
  private final List<StreamEntry> streamQueue = new ArrayList<>();

  private int streamCallIndex = 0;

  MockTransport(List<Scripted> responses) {
    this.responses = responses;
  }

  /** A transport that answers every request with {@code router}'s response for it. */
  static MockTransport ofRouter(java.util.function.Function<HttpRequest, Scripted> router) {
    MockTransport transport = new MockTransport(List.of(new Scripted(500, "no route", null)));
    transport.router = router;
    return transport;
  }

  /** A transport that never answers the requests {@code hangWhen} accepts. */
  static MockTransport ofHanging(java.util.function.Predicate<HttpRequest> hangWhen) {
    MockTransport transport = ofConstant(200, "{\"data\":{\"id\":\"r1\",\"status\":\"RUNNING\"}}");
    transport.hangWhen = hangWhen;
    return transport;
  }

  /** Whether every request left unanswered has been cancelled by the client. */
  boolean allHungCancelled() {
    return !hung.isEmpty() && hung.stream().allMatch(CompletableFuture::isCancelled);
  }

  static MockTransport ofConstant(int status, String body) {
    return new MockTransport(List.of(new Scripted(status, body, null)));
  }

  static Scripted ok(int status, String body) {
    return new Scripted(status, body, null);
  }

  /** As {@link #ok(int, String)}, additionally setting response headers (e.g. pagination ones). */
  static Scripted ok(int status, String body, Map<String, String> headers) {
    return new Scripted(status, body, null, headers);
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
  public synchronized CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest request) {
    int idx = calls++;
    lastHeaders = request.headers();
    lastUrl = request.uri().toString();
    urls.add(lastUrl);
    lastMethod = request.method();
    lastTimeout = request.timeout();
    timeouts.add(lastTimeout);
    lastBodyBytes = readBody(request);
    lastBody = lastBodyBytes == null ? null : new String(lastBodyBytes, StandardCharsets.UTF_8);
    if (lastBody != null) {
      bodies.add(lastBody);
    }
    if (hangWhen.test(request)) {
      CompletableFuture<HttpResponse<byte[]>> never = new CompletableFuture<>();
      hung.add(never);
      return never;
    }
    if (idx >= responses.size()) {
      idx = responses.size() - 1; // repeat the last entry
    }
    Scripted r = router != null ? router.apply(request) : responses.get(idx);
    if (r.error != null) {
      CompletableFuture<HttpResponse<byte[]>> failed = new CompletableFuture<>();
      failed.completeExceptionally(r.error);
      return failed;
    }
    return CompletableFuture.completedFuture(
        new FakeResponse(request.uri(), r.status, r.body, r.headers));
  }

  /**
   * Appends a scripted {@link #sendStreamingAsync} response (status + in-memory body). A test that
   * calls this once (the common case) gets that same response for every streaming call, since the
   * single queued entry repeats; calling it several times scripts a sequence consumed in order.
   */
  synchronized void scriptStream(int status, String body) {
    streamQueue.add(
        new StreamEntry(
            status, new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
  }

  /**
   * As {@link #scriptStream(int, String)}, with an arbitrary, possibly blocking, {@link
   * InputStream} instead of a finite in-memory buffer - so {@code read()} does not naturally reach
   * end-of-stream; this models a live log that only ends when the stream is closed.
   */
  synchronized void scriptStream(int status, InputStream body) {
    streamQueue.add(new StreamEntry(status, body));
  }

  @Override
  public synchronized CompletableFuture<HttpResponse<InputStream>> sendStreamingAsync(
      HttpRequest request) {
    calls++;
    lastUrl = request.uri().toString();
    lastMethod = request.method();
    HttpResponse<InputStream> response;
    if (streamQueue.isEmpty()) {
      Scripted r = responses.get(0);
      response =
          new FakeStreamResponse(request.uri(), r.status, new java.io.ByteArrayInputStream(r.body));
    } else {
      int idx = Math.min(streamCallIndex++, streamQueue.size() - 1);
      StreamEntry entry = streamQueue.get(idx);
      response = new FakeStreamResponse(request.uri(), entry.status, entry.body);
    }
    CompletableFuture<Void> gate = streamGate;
    return gate == null
        ? CompletableFuture.completedFuture(response)
        : gate.thenApply(unused -> response);
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
    private final Map<String, String> headers;

    FakeResponse(URI uri, int status, byte[] body) {
      this(uri, status, body, Map.of());
    }

    FakeResponse(URI uri, int status, byte[] body, Map<String, String> headers) {
      this.uri = uri;
      this.status = status;
      this.body = body;
      this.headers = headers;
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
      Map<String, List<String>> multi = new java.util.LinkedHashMap<>();
      headers.forEach((k, v) -> multi.put(k, List.of(v)));
      return HttpHeaders.of(multi, (a, b) -> true);
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
