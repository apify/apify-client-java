package com.apify.client.build;

import com.apify.client.http.ApiResponse;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.Json;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import com.apify.client.log.LogClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

/** A client for a specific Actor build ({@code /v2/actor-builds/{buildId}}). */
public final class BuildClient {
  private final HttpClientCore http;
  private final ResourceContext ctx;

  public BuildClient(HttpClientCore http, String baseUrl, String id) {
    this.http = http;
    this.ctx = ResourceContext.single(http, baseUrl, ApiPaths.ACTOR_BUILDS, id);
  }

  /** Fetches the build object, or empty if it does not exist. */
  public Optional<Build> get() {
    return getWithWait(null);
  }

  /**
   * Fetches the build, optionally asking the API to wait up to {@code waitForFinishSecs} seconds
   * for the build to finish before responding. The value is clamped so the server always responds
   * before the client's per-request timeout; the API itself caps server-side waiting at 60 seconds.
   * Pass {@code null} for an immediate fetch.
   */
  public Optional<Build> getWithWait(Long waitForFinishSecs) {
    QueryParams params = new QueryParams();
    // Clamp to the client's per-request timeout so a short custom timeout doesn't abort the call.
    params.addLong("waitForFinish", ctx.clampServerWait(waitForFinishSecs));
    return ctx.getResource("", params, Build.class);
  }

  /** Aborts the build and returns its updated state. */
  public Build abort() {
    return ctx.postWithBody("abort", new QueryParams(), null, "", Build.class);
  }

  /** Deletes the build. */
  public void delete() {
    ctx.deleteResource("");
  }

  /**
   * Polls until the build reaches a terminal state or {@code waitSecs} elapses ({@code null} waits
   * indefinitely). Returns the latest build.
   */
  public Build waitForFinish(Long waitSecs) {
    return ctx.waitForFinish(waitSecs, "build", Json.type(Build.class), Build::isTerminal);
  }

  /**
   * Returns the OpenAPI definition generated for the build, or empty if it is not available. The
   * result is the raw OpenAPI document.
   */
  public Optional<JsonNode> getOpenApiDefinition() {
    ApiResponse resp = ctx.getRaw("openapi.json", new QueryParams());
    if (resp == null) {
      return Optional.empty();
    }
    return Optional.of(Json.parse(resp.body(), JsonNode.class));
  }

  /** A client for accessing this build's log. */
  public LogClient log() {
    return LogClient.nested(http, ctx.subUrl(""));
  }
}
