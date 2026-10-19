package com.apify.client.log;

import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A client for accessing the log of an Actor build or run ({@code /v2/logs/{buildOrRunId}}, or the
 * run/build-nested {@code .../log}).
 */
public final class LogClient {
  private final ResourceContext ctx;

  public LogClient(HttpClientCore http, String baseUrl, String id) {
    this.ctx = ResourceContext.single(http, baseUrl, ApiPaths.LOGS, id);
  }

  private LogClient(ResourceContext ctx) {
    this.ctx = ctx;
  }

  /** Creates a log client for a run's or build's nested log endpoint (e.g. {@code .../log}). */
  public static LogClient nested(HttpClientCore http, String base) {
    return nested(http, base, null);
  }

  /** As {@link #nested(HttpClientCore, String)} but inheriting parent query params. */
  public static LogClient nested(HttpClientCore http, String base, QueryParams inherited) {
    return new LogClient(ResourceContext.nestedCollection(http, base, "log", inherited));
  }

  /**
   * Fetches the entire log as text, or empty if the log does not exist.
   *
   * <p>On a client reached through a run/build without an explicit log id (e.g. {@code run.log()},
   * {@code build.log()}), a 404 is ambiguous between "the run/build is gone" and "it has no log
   * yet", so it throws {@link com.apify.client.http.NotFoundError} instead of resolving to empty.
   */
  public CompletableFuture<Optional<String>> get() {
    return get(new LogOptions());
  }

  /** Fetches the log with explicit options (raw, download). See {@link #get()} for 404 handling. */
  public CompletableFuture<Optional<String>> get(LogOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.getRawUnlessAmbiguous("", params)
        .thenApply(
            resp ->
                resp == null
                    ? Optional.empty()
                    : Optional.of(new String(resp.body(), StandardCharsets.UTF_8)));
  }

  /**
   * As {@link #get()}, but resolves to the log's raw bytes instead of decoding them as text -
   * mirroring the reference clients' {@code buffer}/{@code get_as_bytes} option. Useful when the
   * log is not UTF-8 text, or the caller wants to avoid the decoding step. See {@link #get()} for
   * 404 handling.
   */
  public CompletableFuture<Optional<byte[]>> getAsBytes() {
    return getAsBytes(new LogOptions());
  }

  /** As {@link #getAsBytes()}, with explicit options (raw, download). */
  public CompletableFuture<Optional<byte[]>> getAsBytes(LogOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.getRawUnlessAmbiguous("", params)
        .thenApply(resp -> resp == null ? Optional.empty() : Optional.of(resp.body()));
  }

  /**
   * Opens a live, streaming connection to the log and completes with a stream over the log bytes.
   * The caller is responsible for closing the returned {@link InputStream}.
   *
   * <p>Unlike {@link #get()}, this bypasses the buffered/retrying transport so the log can be
   * followed in real time as the run produces it (the {@code stream=1} query parameter). Because
   * the response is consumed incrementally, it is not retried.
   */
  public CompletableFuture<InputStream> stream() {
    return stream(new LogOptions());
  }

  /** Opens a live log stream with explicit options (raw, download). */
  public CompletableFuture<InputStream> stream(LogOptions options) {
    QueryParams params = new QueryParams();
    params.addBool("stream", true);
    options.apply(params);
    String url = ctx.mergedParams(params).applyToUrl(ctx.subUrl(""));
    return ctx.http.streamOrThrow(url);
  }
}
