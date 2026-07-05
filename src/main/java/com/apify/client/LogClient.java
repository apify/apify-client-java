package com.apify.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A client for accessing the log of an Actor build or run ({@code /v2/logs/{buildOrRunId}}, or the
 * run/build-nested {@code .../log}).
 */
public final class LogClient {
  private final ResourceContext ctx;

  LogClient(HttpClientCore http, String baseUrl, String resourcePath, String id) {
    this.ctx = ResourceContext.single(http, baseUrl, resourcePath, id);
  }

  private LogClient(ResourceContext ctx) {
    this.ctx = ctx;
  }

  /** Creates a log client for a run's or build's nested log endpoint (e.g. {@code .../log}). */
  static LogClient nested(HttpClientCore http, String base) {
    return nested(http, base, null);
  }

  /** As {@link #nested(HttpClientCore, String)} but inheriting parent query params. */
  static LogClient nested(HttpClientCore http, String base, QueryParams inherited) {
    return new LogClient(ResourceContext.collection(http, base, "log").seedParams(inherited));
  }

  /** Fetches the entire log as text, or empty if the log does not exist. */
  public Optional<String> get() {
    return get(new LogOptions());
  }

  /** Fetches the log with explicit options (raw, download). */
  public Optional<String> get(LogOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    ApiResponse resp = ctx.getRaw("", params);
    if (resp == null) {
      return Optional.empty();
    }
    return Optional.of(new String(resp.body, StandardCharsets.UTF_8));
  }

  /**
   * Opens a live, streaming connection to the log and returns a stream over the log bytes. The
   * caller is responsible for closing the returned {@link InputStream}.
   *
   * <p>Unlike {@link #get()}, this bypasses the buffered/retrying transport so the log can be
   * followed in real time as the run produces it (the {@code stream=1} query parameter). Because
   * the response is consumed incrementally, it is not retried.
   */
  public InputStream stream() {
    return stream(new LogOptions());
  }

  /** Opens a live log stream with explicit options (raw, download). */
  public InputStream stream(LogOptions options) {
    QueryParams params = new QueryParams();
    params.addBool("stream", true);
    options.apply(params);
    String url = ctx.mergedParams(params).applyToUrl(ctx.subUrl(""));

    HttpResponse<InputStream> resp = ctx.http.stream(url);
    if (resp.statusCode() >= HttpClientCore.MAX_SUCCESS_STATUS) {
      byte[] body = drain(resp.body());
      throw HttpClientCore.buildApiError(
          resp.statusCode(), body, 1, "GET", HttpClientCore.extractPath(url));
    }
    return resp.body();
  }

  private static byte[] drain(InputStream in) {
    try (InputStream stream = in) {
      return stream.readAllBytes();
    } catch (IOException e) {
      return new byte[0];
    }
  }
}
