package com.apify.client.actor;

import com.apify.client.build.Build;
import com.apify.client.http.ApiResponse;
import com.apify.client.http.ServerActorException;
import com.apify.client.http.Timeout;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.CancellableFuture;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.Json;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.RequestPolicy;
import com.apify.client.internal.ResourceContext;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * A client for the server interface of a specific Actor, from {@link ActorClient#server()}.
 *
 * <p>A Server Actor (formerly Actor Standby) runs a web server. You call it over HTTP, and the
 * platform keeps a run ready to answer, so each call skips the run startup. This client sends
 * requests to the Actor's web server and gets its URL and OpenAPI specification.
 *
 * <pre>{@code
 * ActorServerClient server = client.actor("username/actor-name").server();
 * ApiResponse response =
 *     server.request("/search", new ActorServerRequestOptions().param("q", "apify")).join();
 * System.out.println(response.bodyAsString());
 * }</pre>
 *
 * @see <a href="https://docs.apify.com/platform/actors/running/standby">Standby mode</a>
 */
public final class ActorServerClient {

  /** The tag whose build serves the server interface when the Actor sets no build. */
  private static final String DEFAULT_BUILD_TAG = "latest";

  /** A build number like {@code 0.1.23}: the version {@code 0.1} and the sequence number 23. */
  private static final Pattern BUILD_NUMBER_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

  /** The path of an Actor's builds, relative to the Actor. */
  private static final String BUILDS_PATH = "builds";

  /** The path of a request that names none: the root of the server. */
  private static final String ROOT_PATH = "/";

  private static final String FRAGMENT_START = "#";

  private final HttpClientCore http;
  private final ResourceContext ctx;
  private final String baseUrl;
  private final String id;

  /**
   * The server URL. Getting it takes an API call for the Actor, so it is cached for {@link #getUrl}
   * and the requests. It is pending while the first lookup runs, so concurrent first calls share
   * one API request, and it is cleared when the lookup fails, so the next call tries again.
   */
  private final AtomicReference<CompletableFuture<String>> url = new AtomicReference<>();

  ActorServerClient(HttpClientCore http, String baseUrl, String id) {
    this.http = http;
    this.ctx = ResourceContext.single(http, baseUrl, ApiPaths.ACTORS, id);
    this.baseUrl = baseUrl;
    this.id = id;
  }

  /**
   * Returns the URL of the Actor's server.
   *
   * <p>The URL is the Actor's {@code standbyUrl}, fetched from the API by the first call of this
   * method or {@link #getOpenApiSpec()} and reused by later calls on this client. Use it to call
   * the server with another HTTP client, for example one generated from the server's OpenAPI
   * specification. The API request takes the {@link Timeout#SHORT} timeout.
   *
   * @return the URL of the Actor's server; fails with a {@link ServerActorException} when the Actor
   *     is not a Server Actor, and with a {@link com.apify.client.http.NotFoundError} when the
   *     Actor does not exist
   */
  public CompletableFuture<String> getUrl() {
    return getUrl(Timeout.SHORT);
  }

  /**
   * As {@link #getUrl()}, with {@code timeout} for the API request that fetches the URL. It is not
   * used when the URL is already cached, nor while another caller's lookup is running, since the
   * timeout of the caller that started it applies.
   *
   * @throws IllegalArgumentException if {@code timeout} is {@code null}
   */
  public CompletableFuture<String> getUrl(Timeout timeout) {
    requireTimeout(timeout);
    // A copy, so that cancelling the returned future leaves the shared lookup alone.
    return sharedUrl(timeout).copy();
  }

  private static void requireTimeout(Timeout timeout) {
    if (timeout == null) {
      throw new IllegalArgumentException("timeout must not be null");
    }
  }

  /** The cached lookup of the server URL, started if there is none. */
  private CompletableFuture<String> sharedUrl(Timeout timeout) {
    while (true) {
      CompletableFuture<String> cached = url.get();
      if (cached != null) {
        return cached;
      }
      CancellableFuture<String> lookup = new CancellableFuture<>();
      if (url.compareAndSet(null, lookup)) {
        CompletableFuture<Actor> fetch;
        try {
          fetch = fetchServerActor(timeout);
        } catch (RuntimeException e) {
          // A lookup that cannot even start must not stay cached, or every later call would wait on
          // it.
          fetch = CompletableFuture.failedFuture(e);
        }
        fetch.whenComplete(
            (actor, error) -> {
              if (error == null) {
                lookup.complete(actor.getStandbyUrl());
              } else {
                url.compareAndSet(lookup, null);
                lookup.completeExceptionally(HttpClientCore.unwrapCompletion(error));
              }
            });
        return lookup;
      }
    }
  }

  /**
   * Returns the OpenAPI specification of the Actor's server.
   *
   * <p>The specification is the web server schema from the Actor's definition, taken from the build
   * that serves the server interface: the build set in {@code actorStandby.build} (a build tag or a
   * build number), or {@code latest} if none is set. Use it to generate an HTTP client for the
   * server, and point that client at the URL returned by {@link #getUrl()}.
   *
   * @return the OpenAPI specification, or empty if the Actor's definition does not include one;
   *     fails with a {@link ServerActorException} when the Actor is not a Server Actor or the build
   *     that serves its server interface does not exist, and with a {@link
   *     com.apify.client.http.NotFoundError} when the Actor does not exist
   * @see <a
   *     href="https://docs.apify.com/platform/actors/development/actor-definition/web-server-schema">Web
   *     server schema</a>
   */
  public CompletableFuture<Optional<JsonNode>> getOpenApiSpec() {
    return getOpenApiSpec(Timeout.SHORT);
  }

  /** As {@link #getOpenApiSpec()}, with {@code timeout} for each API request it makes. */
  public CompletableFuture<Optional<JsonNode>> getOpenApiSpec(Timeout timeout) {
    requireTimeout(timeout);
    return fetchServerActor(timeout)
        .thenCompose(
            actor -> {
              url.set(CancellableFuture.completed(actor.getStandbyUrl()));
              String buildRef = buildRefOf(actor);
              Optional<String> taggedBuildId = taggedBuildId(actor, buildRef);
              CompletableFuture<Optional<String>> buildId =
                  taggedBuildId.isPresent()
                      ? CompletableFuture.completedFuture(taggedBuildId)
                      // Not a tag, so look `buildRef` up as a build number, which the API cannot
                      // resolve directly.
                      : findBuildIdByNumber(buildRef, timeout);
              return buildId.thenCompose(found -> fetchBuild(found, buildRef, timeout));
            })
        .thenApply(
            build ->
                Optional.ofNullable(build.getActorDefinition())
                    .map(definition -> definition.getWebServerSchema())
                    .filter(schema -> !schema.isNull()));
  }

  /**
   * Sends an HTTP request to the Actor's server and completes with its response, the body buffered.
   *
   * <p>The request goes to the URL returned by {@link #getUrl()} and carries the client's token in
   * the {@code X-Apify-Authorization} header, which the platform consumes, so it never reaches the
   * Actor's code. Headers you set, {@code Authorization} included, reach the Actor's server as they
   * are. The first request to an idle Actor waits for the platform to start a server run, which is
   * why the timeout defaults to {@link Timeout#LONG}.
   *
   * <p>The future completes with any response of the server, error statuses included, so check
   * {@link ApiResponse#statusCode()}. A rate-limit (429) or Service Unavailable (503) response is
   * retried with the client's retry settings, and the last one is returned once the retries run
   * out. The Actor's code can return these statuses too, and then it gets the request again. A
   * redirect is returned as it is, since following it would send the token to a host the Actor's
   * code picked. A transport error, a timeout included, is not retried, since the Actor may have
   * handled the request before it failed. Request bodies are sent uncompressed unless {@link
   * ActorServerRequestOptions#compression} is set, since the Actor's server may not decode a
   * compressed body.
   *
   * @param path the path on the Actor's server, for example {@code /search}. It is always resolved
   *     against the server URL, so the request, and the token with it, cannot be sent to another
   *     host. Pass the query as {@link ActorServerRequestOptions#param params}. A fragment ({@code
   *     #...}) is dropped, since it is never sent to the server.
   * @param options the method, query, headers, body, compression and timeout; {@code null} sends a
   *     plain {@code GET}
   * @return the response; fails with a {@link ServerActorException} when the Actor is not a Server
   *     Actor, and with a {@link com.apify.client.http.NotFoundError} when the Actor does not exist
   */
  public CompletableFuture<ApiResponse> request(String path, ActorServerRequestOptions options) {
    ActorServerRequestOptions opts = options != null ? options : new ActorServerRequestOptions();
    byte[] body = Json.toRequestBody(opts.bodyValue());
    return getUrl()
        .thenCompose(
            serverUrl ->
                http.call(
                    opts.methodValue(),
                    requestUrl(serverUrl, path, opts),
                    body,
                    contentTypeOf(opts),
                    opts.headersValue(),
                    opts.timeoutValue(),
                    false,
                    RequestPolicy.server(opts.compressionValue())));
  }

  /** A {@code GET} request to {@code path} on the Actor's server; see {@link #request}. */
  public CompletableFuture<ApiResponse> request(String path) {
    return request(path, null);
  }

  /** A {@code GET} request to the root of the Actor's server; see {@link #request}. */
  public CompletableFuture<ApiResponse> request() {
    return request(ROOT_PATH, null);
  }

  /**
   * As {@link #request}, but completes with the response as soon as its headers arrive, with the
   * body unread. A response whose body is large, or never ends, can be processed in chunks this
   * way. The body holds its connection open until it is read to the end or closed, so close the
   * returned {@link ActorServerStreamResponse}. A rate-limit (429) or Service Unavailable (503)
   * response is retried like for {@link #request}, with the discarded responses closed; a transport
   * error is not. {@link ActorServerRequestOptions#timeout} bounds the wait for the server to start
   * responding.
   */
  public CompletableFuture<ActorServerStreamResponse> requestStream(
      String path, ActorServerRequestOptions options) {
    ActorServerRequestOptions opts = options != null ? options : new ActorServerRequestOptions();
    byte[] body = Json.toRequestBody(opts.bodyValue());
    return getUrl()
        .thenCompose(
            serverUrl ->
                http.streamRequest(
                    opts.methodValue(),
                    requestUrl(serverUrl, path, opts),
                    body,
                    contentTypeOf(opts),
                    opts.headersValue(),
                    opts.timeoutValue(),
                    RequestPolicy.server(opts.compressionValue())))
        .thenApply(
            response ->
                new ActorServerStreamResponse(
                    response.statusCode(), response.headers(), response.body()));
  }

  /** Fetches the Actor and checks that it is a Server Actor. */
  private CompletableFuture<Actor> fetchServerActor(Timeout timeout) {
    return ctx.getResourceRequired("", new QueryParams(), Actor.class, timeout)
        .thenApply(
            actor -> {
              String standbyUrl = actor == null ? null : actor.getStandbyUrl();
              if (standbyUrl == null || standbyUrl.isEmpty()) {
                throw new ServerActorException("Actor " + id + " is not a Server Actor.");
              }
              return actor;
            });
  }

  /** The tag or number of the build that serves the server interface. */
  private static String buildRefOf(Actor actor) {
    ActorStandby standby = actor.getActorStandby();
    String build = standby == null ? null : standby.getBuild();
    return build == null || build.isEmpty() ? DEFAULT_BUILD_TAG : build;
  }

  /** The id of the build the tag {@code buildRef} points to, or empty if it is not a tag. */
  private static Optional<String> taggedBuildId(Actor actor, String buildRef) {
    JsonNode tagged = actor.getTaggedBuilds();
    JsonNode buildId = tagged == null ? null : tagged.path(buildRef).path("buildId");
    return buildId != null && buildId.isTextual()
        ? Optional.of(buildId.asText())
        : Optional.empty();
  }

  /**
   * Looks {@code buildNumber} up in the Actor's builds, which are listed newest first and whose
   * sequence numbers within a version grow over time, so meeting an older build of the same version
   * means the build does not exist. Completes with empty for anything that is not a build number.
   */
  private CompletableFuture<Optional<String>> findBuildIdByNumber(
      String buildNumber, Timeout timeout) {
    Optional<int[]> target = parseBuildNumber(buildNumber);
    if (target.isEmpty()) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    ResourceContext builds = ResourceContext.collection(http, ctx.subUrl(""), BUILDS_PATH);
    return findBuildIdByNumber(builds, buildNumber, target.get(), 0, timeout);
  }

  private CompletableFuture<Optional<String>> findBuildIdByNumber(
      ResourceContext builds, String buildNumber, int[] target, long offset, Timeout timeout) {
    QueryParams params = new QueryParams().addBool("desc", true).addLong("offset", offset);
    return builds
        .listResource("", params, Build.class, timeout)
        .thenCompose(
            page -> {
              for (Build build : page.getItems()) {
                if (buildNumber.equals(build.getBuildNumber())) {
                  return CompletableFuture.completedFuture(Optional.of(build.getId()));
                }
                if (isOlderBuild(build.getBuildNumber(), target)) {
                  return CompletableFuture.completedFuture(Optional.<String>empty());
                }
              }
              if (page.getItems().isEmpty()) {
                return CompletableFuture.completedFuture(Optional.<String>empty());
              }
              return findBuildIdByNumber(
                  builds, buildNumber, target, offset + page.getItems().size(), timeout);
            });
  }

  private CompletableFuture<Build> fetchBuild(
      Optional<String> buildId, String buildRef, Timeout timeout) {
    if (buildId.isEmpty()) {
      return CompletableFuture.failedFuture(buildDoesNotExist(buildRef));
    }
    return ResourceContext.single(http, baseUrl, ApiPaths.ACTOR_BUILDS, buildId.get())
        .getResource("", new QueryParams(), Build.class, timeout)
        .thenApply(build -> build.orElseThrow(() -> buildDoesNotExist(buildRef)));
  }

  private ServerActorException buildDoesNotExist(String buildRef) {
    return new ServerActorException("Build " + buildRef + " of Actor " + id + " does not exist.");
  }

  /** Splits a build number like {@code 0.1.23} into its parts, or empty for anything else. */
  private static Optional<int[]> parseBuildNumber(String buildNumber) {
    if (buildNumber == null) {
      return Optional.empty();
    }
    Matcher match = BUILD_NUMBER_PATTERN.matcher(buildNumber);
    if (!match.matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new int[] {
          Integer.parseInt(match.group(1)),
          Integer.parseInt(match.group(2)),
          Integer.parseInt(match.group(3))
        });
  }

  /**
   * Whether {@code buildNumber} belongs to the same version as {@code target} and comes before it.
   */
  private static boolean isOlderBuild(String buildNumber, int[] target) {
    return parseBuildNumber(buildNumber)
        .filter(parsed -> parsed[0] == target[0] && parsed[1] == target[1] && parsed[2] < target[2])
        .isPresent();
  }

  /**
   * The URL of a request: {@code path} resolved against the server URL, without its fragment and
   * with the query parameters appended.
   */
  private static String requestUrl(
      String serverUrl, String path, ActorServerRequestOptions options) {
    String requested = path == null ? ROOT_PATH : path;
    String withoutFragment = requested.split(FRAGMENT_START, 2)[0];
    String serverPath = withoutFragment.replaceFirst("^/+", "");
    String base = serverUrl.replaceFirst("/+$", "");
    return options.queryParams().applyToUrl(base + "/" + serverPath);
  }

  /**
   * The content type of the body: the one the caller set, JSON for a body that is serialized, and
   * none for a body that is sent as it is.
   */
  private static String contentTypeOf(ActorServerRequestOptions options) {
    String set = options.contentTypeHeader();
    if (set != null) {
      return set;
    }
    Object body = options.bodyValue();
    boolean sentAsIs = body == null || body instanceof byte[] || body instanceof String;
    return sentAsIs ? null : ResourceContext.CONTENT_TYPE_JSON;
  }
}
