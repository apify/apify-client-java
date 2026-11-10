# Actors, versions & environment variables

Access the Actor collection with `client.actors()` and a single Actor with `client.actor(id)`,
where `id` is an Actor ID or `username~name` (a `/` in the id is accepted and normalized).

## `ActorCollectionClient`

| Method | Description |
|---|---|
| `list(ActorListOptions)` | List the account's Actors. Completes with `PaginationList<Actor>`. |
| `iterate(ActorListOptions, Long chunkSize)` | Lazy `Flow.Publisher<Actor>` over all matches; the options' `limit` caps the total yielded (`null`/unset or non-positive = all), `chunkSize` sets the page size (`null` = server default). |
| `create(Object)` | Create a new Actor from a JSON-serializable definition. Completes with `Actor`. |

`ActorListOptions` adds `my(Boolean)` (only Actors owned by the current user) and
`sortBy(String)` (e.g. `createdAt`, `stats.lastRunStartedAt`) on top of the standard offset/limit/desc.

```java
PaginationList<Actor> mine = client.actors().list(new ActorListOptions().my(true).limit(5L)).join();
for (Actor actor : mine.getItems()) {
  System.out.println(actor.getName());
}
```

Create an Actor with a `SOURCE_FILES` version (`sourceType` is one of `SOURCE_FILES`, `GIT_REPO`,
`TARBALL`, `GITHUB_GIST`, `SOURCE_CODE`; a source-file `format` is `TEXT` or `BASE64`):

```java
Actor created = client.actors().create(Map.of(
    "name", "my-actor",
    "isPublic", false,
    "versions", List.of(Map.of(
        "versionNumber", "0.0",
        "sourceType", "SOURCE_FILES",
        "buildTag", "latest",
        "sourceFiles", List.of(
            Map.of("name", "Dockerfile", "format", "TEXT",
                   "content", "FROM apify/actor-node:20\nCOPY . ./\nCMD node main.js"),
            Map.of("name", "main.js", "format", "TEXT",
                   "content", "console.log('hi');")))))).join();
```

## `ActorClient`

| Method | Description |
|---|---|
| `get()` | Fetch the Actor. Completes with `Optional<Actor>`. |
| `update(Object)` | Update the Actor with the given fields. Completes with `Actor`. |
| `delete()` | Delete the Actor. |
| `start(Object input, ActorStartOptions)` | Start a run, returning immediately. Completes with `ActorRun`. The `input` is any JSON-serializable value, or a `byte[]`/`String` that is sent as it is (set the matching `ActorStartOptions.contentType(...)`), or `null`. |
| `call(Object input, ActorStartOptions, Long waitSecs)` | Start a run and poll until it finishes (`null` waits indefinitely); does **not** stream the run's log. Completes with `ActorRun`. |
| `call(Object input, ActorCallOptions, Long waitSecs)` | As above, additionally streaming the run's log for the duration of the wait by default (matching the reference client's `call` defaulting `options.log` to `'default'`). Use `ActorCallOptions.disableLogStreaming()` to opt out, or `logOptions(StreamedLogOptions)` for a custom destination. |
| `validateInput(Object input)` / `validateInput(Object input, ValidateInputOptions)` | Validate an input against the Actor's input schema. Completes with `boolean`. `ValidateInputOptions` fields (optional): `build` (`String`), `contentType` (`String`). |
| `build(String versionNumber, ActorBuildOptions)` | Build a version. Completes with `Build`. |
| `defaultBuild(Long waitForFinish)` | Resolve the default build. Completes with `BuildClient`. |
| `lastRun(String status)` / `lastRun(LastRunOptions)` | A `RunClient` for the last run. |
| `builds()` / `runs()` / `versions()` | Nested collection clients. |
| `webhooks()` | Read-only nested webhook collection (`NestedWebhookCollectionClient`, `list` + `iterate`, no `create`). |
| `version(String)` | An `ActorVersionClient`. |
| `server()` | The `ActorServerClient` for the Actor's web server (a Server Actor, formerly Actor Standby); the same instance on every call. See [Server Actors](#server-actors). |
| `standby()` | Deprecated alias of `server()` that logs a warning; will be removed in a future major version. |

`ActorStartOptions` fields (all optional): `build(String)` (the tag or number of the build to run,
e.g. `latest`, `0.1.2`), `memoryMbytes(Long)` (memory in megabytes allocated for the run),
`timeoutSecs(Long)` (run timeout in seconds; `0` means no timeout), `waitForFinish(Long)` (maximum
seconds to wait server-side for the run to finish, max 60), `maxItems(Long)` (maximum dataset items
to charge, pay-per-result Actors), `maxTotalChargeUsd(Double)` (maximum total charge in USD,
pay-per-event Actors), `contentType(String)` (content type of the input body, defaults to
`application/json`), `restartOnError(Boolean)` (restart the run if it fails),
`forcePermissionLevel(String)` (override the Actor's permission level for this run:
`LIMITED_PERMISSIONS`/`FULL_PERMISSIONS`), `webhooks(List<Object>)` — ad-hoc webhook definitions
(each a JSON-serializable `Map`, as in [Webhooks](webhooks.md)) that the client base64-encodes on the
wire — and `waitForResources(boolean)`/`waitForResources(Long maxSecs)`: retry the start, every 10
seconds, while the account lacks the memory or a concurrent-run slot for the run (`true` retries
until it starts — `ActorStartOptions.WAIT_FOR_RESOURCES_INDEFINITELY`; a bound throws the last such
error once exceeded; omitted, the default, does not retry at all).

`ActorCallOptions` (for the log-streaming `call` overload) mirrors `ActorStartOptions` field for
field, but omits `waitForFinish`: that field asks the API to hold the HTTP response open
server-side while the run finishes, which is redundant with (and wastes a request slot next to)
`call`'s own client-side `waitSecs` polling. It adds `disableLogStreaming()` (matching the
reference client's `log: null`) and `logOptions(StreamedLogOptions)` (a custom destination/prefix,
matching a custom `Log` instance) — see [Streamed log redirection](runs.md#streamed-log-redirection).

`lastRun(String status)` filters only by status; `lastRun(LastRunOptions)` also accepts an origin
filter. `LastRunOptions` has fluent setters `status(String)` (e.g. `SUCCEEDED`, `RUNNING`) and
`origin(String)` (e.g. `API`, `WEB`, `SCHEDULER`); leave a setter uncalled to omit that filter.

```java
Optional<ActorRun> last =
    client.actor("apify/hello-world").lastRun(new LastRunOptions().status("SUCCEEDED").origin("API")).get().join();
```

```java
ActorRun run = client.actor("apify/hello-world")
    .call(Map.of("greeting", "hi"), new ActorStartOptions().memoryMbytes(512L), 120L)
    .join();
System.out.println(run.getStatus());
```

`Actor` fields: `getId()`, `getUserId()`, `getName()`, `getUsername()`, `getTitle()`,
`getDescription()`, `isPublic()`, `getCreatedAt()`, `getModifiedAt()`, `getStats()` (`ActorStats` —
`getTotalBuilds()`/`getTotalRuns()`/`getTotalUsers()`/`getTotalUsers7Days()`/
`getTotalUsers30Days()`/`getTotalUsers90Days()`/`getTotalMetamorphs()` as `Long`,
`getLastRunStartedAt()` as `Instant`), `getVersions()` (`List<ActorVersion>`),
`getPricingInfos()` (`List<JsonNode>`; shape depends on pricing model), `getDefaultRunOptions()`
(`ActorDefaultRunOptions` — `getBuild()`, `getTimeoutSecs()`/`getMemoryMbytes()` as `Long`,
`getRestartOnError()` as `Boolean`), `getTaggedBuilds()` (`JsonNode`; dynamic tag-name keys),
`getIsDeprecated()` (`Boolean`), `getDeploymentKey()`, `getSeoTitle()`, `getSeoDescription()`,
`getCategories()` (`List<String>`), `getStandbyUrl()` (the URL of the Actor's server, `null` unless
Standby mode is enabled), `getActorStandby()` (`ActorStandby`, from
`com.apify.client.actor`, nullable — see [Tasks](tasks.md) for its field list; only on `Actor` does
it additionally expose
`getIsEnabled()`), `getActorPermissionLevel()` (e.g. `"OWNER"`), plus `getExtra()` for any
unmodelled fields.

## Server Actors

A [Server Actor](https://docs.apify.com/platform/actors/running/standby) (formerly Actor Standby)
runs a web server. You call it over HTTP, and the platform keeps a run ready to answer, so each
call skips the run startup. `client.actor(id).server()` returns an `ActorServerClient` that sends
requests to the Actor's server with your token, and returns the server's URL and OpenAPI
specification. The platform and the API still use the name Actor Standby, for example in the
Actor's `standbyUrl` and `actorStandby` fields.

| Method | Description |
|---|---|
| `request(String path, ActorServerRequestOptions)` | Send an HTTP request to the Actor's server. Completes with an `ApiResponse` (`statusCode()`, `headers()`, the buffered `body()` bytes, and the helpers `bodyAsString()` and `bodyAs(Class<T>)` that parse JSON). It completes with **any** response of the server, error statuses included, so check `statusCode()`. `request(String path)` sends a `GET`, `request()` a `GET` to the root. |
| `requestStream(String path, ActorServerRequestOptions)` | As `request`, but completes as soon as the headers arrive with an `ActorServerStreamResponse` (`statusCode()`, `headers()`, an unread `body()` `InputStream`). Close it (it is `AutoCloseable`) when done. A `429`/`503` is retried like for `request`. |
| `getUrl()` / `getUrl(Timeout)` | The URL of the Actor's server (its `standbyUrl`), fetched once and cached on the client. Use it to call the server with another HTTP client. Completes with `String`. The `Timeout` (`Timeout.SHORT` by default) is for the API request that fetches it. |
| `getOpenApiSpec()` / `getOpenApiSpec(Timeout)` | The Actor's web server OpenAPI specification, from the build that serves the server (`actorStandby.build`: a tag or a build number, `latest` by default). Completes with `Optional<JsonNode>` (empty when the Actor's definition has no web server schema). The `Timeout` (`Timeout.SHORT` by default) is for each API request it makes. |

`ActorServerRequestOptions` fields (all optional): `method(String)` (`GET` by default; also `HEAD`,
`POST`, `PUT`, `DELETE`, `OPTIONS`, `TRACE`, `PATCH`), `param(String, Object)`/`params(Map)` (query
parameters, converted as for API calls: `null` is left out, a boolean is sent as `1`/`0`, a
collection comma-joined), `header(String, String)`/`headers(Map)`, `body(Object)` (a `byte[]` or
`String` is sent as it is; anything else is serialized to JSON and sent as `application/json`
unless you set a `Content-Type` header), `parentRequestId(String)` (the `X-Actor-Request-ID` header of
the request your multi-tenant Server Actor is handling, sent on as the same header so the called
Actor charges at that request's pricing tier; use a client created with the run's `APIFY_TOKEN`),
`compression(HttpCompressor)` and `timeout(Timeout)`
(`Timeout.LONG` by default, since the first request to an idle Actor waits for the platform to
start a server run).

```java
ActorServerClient server = client.actor("username/actor-name").server();

// Send a GET request to the /search endpoint of the Actor's server.
// The future completes with error responses too, so check the status first.
ApiResponse search =
    server.request("/search", new ActorServerRequestOptions().param("q", "apify")).join();
if (search.statusCode() != 200) {
  throw new IllegalStateException("Search failed with status " + search.statusCode());
}
System.out.println(search.bodyAsString());

// Send a POST request with a JSON body.
ApiResponse created =
    server
        .request(
            "/items",
            new ActorServerRequestOptions().method("POST").body(Map.of("url", "https://apify.com")))
        .join();
System.out.println(created.statusCode());
```

```java
ActorServerClient server = client.actor("username/actor-name").server();

// Get the URL of the Actor's server.
System.out.println(server.getUrl().join());

// Get the OpenAPI specification and list the server's endpoints.
Optional<JsonNode> spec = server.getOpenApiSpec().join();
spec.ifPresent(openApi -> openApi.get("paths").propertyNames().forEach(System.out::println));
```

How the request is sent, so that the token and the Actor's code stay apart:

- The token travels in the `X-Apify-Authorization` header, which the platform consumes before the
  request reaches the Actor, so the `Authorization` header stays free for the Actor's own
  authentication. Headers you pass, `Authorization` included, reach the server as they are.
- The `path` is always resolved against the server URL, so the request, and the token with it,
  cannot be sent to another host. A fragment (`#...`) is dropped, since it is never sent.
- A `429` or `503` response, of `requestStream` too, is retried with the client's retry settings (the Actor's code can return
  these statuses too, and then it gets the request again), and the last one is returned once the
  retries run out. Other statuses are returned without a retry. A transport error, a timeout
  included, is not retried, since the Actor may have handled the request before it failed.
- A redirect is returned as it is, since following it would send the token to a host the Actor's
  code picked. A custom `HttpTransport` has to support that (see the
  [transport contract](../README.md#httptransport-contract)): a transport that does not fails such a
  request instead of risking the token.
- The body is sent uncompressed unless you set `compression(...)`, since the Actor's server may not
  decode a compressed body.
- An Actor that is not a Server Actor fails the call with a `ServerActorException`, and an Actor
  that does not exist with a `NotFoundError`.

`Actor` versions with `SOURCE_FILES` or `TARBALL` sources can set an `actorPath`, the Actor's
directory inside the uploaded source (for example `actors/typescript-actor` in a monorepo), when
you create or update them; `ActorVersion.getActorPath()` and `BuildActVersion.getActorPath()` read
it. An update that sends `sourceType`, `sourceFiles` or `tarballUrl` without `actorPath` removes the
stored one, so send it again with every upload.

## `ActorVersionClient` and `ActorVersionCollectionClient`

`client.actor(id).versions()` lists/creates versions; `client.actor(id).version(v)` reads, updates
and deletes a single version and exposes its environment variables.

### `ActorVersionCollectionClient` — `client.actor(id).versions()`

| Method | Description |
|---|---|
| `list(ListOptions)` | List the Actor's versions. Completes with `PaginationList<ActorVersion>`. The endpoint takes no query parameters; `options` (`offset`/`limit`/`desc`) is accepted only for signature consistency with every other `list(ListOptions)` and has no effect — pass `null`. |
| `iterate(ListOptions)` | Lazy `Flow.Publisher<ActorVersion>` over all versions; `limit` caps the total (`null`/unset or non-positive = all), applied client-side after the single fetch. `offset` has no effect and there is no page size to tune. |
| `create(Object version)` | Create a version. Completes with `ActorVersion`. |

### `ActorVersionClient` — `client.actor(id).version(v)`

| Method | Description |
|---|---|
| `get()` | Fetch the version. Completes with `Optional<ActorVersion>`. |
| `update(Object)` | Update the version. Completes with `ActorVersion`. |
| `delete()` | Delete the version. |
| `envVars()` | An `ActorEnvVarCollectionClient` for the version's environment variables. |
| `envVar(String name)` | An `ActorEnvVarClient` for a single environment variable. |

```java
ActorVersion version = client.actor("me/my-actor").version("0.0").get().join().orElseThrow();
System.out.println(version.getSourceType());
```

`ActorVersion` fields: `getVersionNumber()`, `getSourceType()`, plus `getExtra()` for any unmodelled
fields.

## `ActorEnvVarClient` and `ActorEnvVarCollectionClient`

Attach environment variables to a version. `ActorEnvVar` has a `(name, value)` constructor plus
fluent setters `setName`, `setValue`, `setIsSecret(Boolean)` (when secret, the value is stored
encrypted), and matching getters `getName()`, `getValue()`, `getIsSecret()`.

### `ActorEnvVarCollectionClient` — `client.actor(id).version(v).envVars()`

| Method | Description |
|---|---|
| `list()` | List the version's environment variables. Completes with `PaginationList<ActorEnvVar>`. |
| `iterate()` | `Flow.Publisher<ActorEnvVar>` over the variables. The env-var collection is not paginated (all variables are returned at once), so this iterates a single fetched page; provided for API consistency. |
| `create(ActorEnvVar)` | Create an environment variable. Completes with `ActorEnvVar`. |

### `ActorEnvVarClient` — `client.actor(id).version(v).envVar(name)`

| Method | Description |
|---|---|
| `get()` | Fetch the environment variable. Completes with `Optional<ActorEnvVar>`. |
| `update(ActorEnvVar)` | Update the environment variable. Completes with `ActorEnvVar`. |
| `delete()` | Delete the environment variable. |

```java
client.actor("me/my-actor").version("0.0").envVars()
    .create(new ActorEnvVar("API_KEY", "secret").setIsSecret(true))
    .join();
Optional<ActorEnvVar> ev = client.actor("me/my-actor").version("0.0").envVar("API_KEY").get().join();
```
