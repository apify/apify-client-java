package com.apify.client.log;

import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.io.IOException;
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

  /** Fetches the entire log as text, or empty if the log does not exist. */
  public CompletableFuture<Optional<String>> get() {
    return get(new LogOptions());
  }

  /** Fetches the log with explicit options (raw, download). */
  public CompletableFuture<Optional<String>> get(LogOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.getRaw("", params)
        .thenApply(
            resp ->
                resp == null
                    ? Optional.empty()
                    : Optional.of(new String(resp.body(), StandardCharsets.UTF_8)));
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

    return ctx.http
        .streamAsync(url)
        .thenApply(
            resp -> {
              if (resp.statusCode() >= HttpClientCore.MAX_SUCCESS_STATUS) {
                byte[] body = drain(resp.body());
                throw HttpClientCore.buildApiError(
                    resp.statusCode(), body, 1, "GET", HttpClientCore.extractPath(url));
              }
              return resp.body();
            });
  }

  private static byte[] drain(InputStream in) {
    try (InputStream stream = in) {
      return stream.readAllBytes();
    } catch (IOException e) {
      return new byte[0];
    }
  }
}
