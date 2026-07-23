# Apify API client for Java

> **Official, but experimental — AI-generated and AI-maintained.** This is an official Apify client,
> but it is experimental: it is generated and maintained by AI. Review the code before relying on it
> in production and report issues on the repository.

A resource-oriented Java client for the [Apify API](https://docs.apify.com/api/v2), mirroring the
official [JavaScript](https://github.com/apify/apify-client-js) reference client: start from an
`ApifyClient`, then drill down into resources (Actors, runs, datasets, key-value stores, request
queues, tasks, schedules, webhooks, the store, users and logs).

## Requirements

- Java 17 or newer.

## Installation

The client is published to [Maven Central](https://central.sonatype.com/artifact/com.apify/apify-client).

Maven (Maven Central is a default repository, so no extra configuration is needed):

```xml
<dependency>
  <groupId>com.apify</groupId>
  <artifactId>apify-client</artifactId>
  <version>0.5.0</version>
</dependency>
```

Gradle — ensure `mavenCentral()` is in your `repositories`, then add the dependency:

```groovy
repositories {
  mavenCentral()
}

dependencies {
  implementation 'com.apify:apify-client:0.5.0'
}
```

## Quick start

After adding the dependency (above), create an `ApifyClient`, then drill down into a resource:

```java
import com.apify.client.ApifyClient;
import com.apify.client.run.ActorRun;
import com.apify.client.actor.ActorStartOptions;

class HelloApify {
  public static void main(String[] args) {
    // Your API token from https://console.apify.com/settings/integrations
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));
    ActorRun run = client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L).join();
    System.out.println("Run " + run.getId() + " finished with status " + run.getStatus());
  }
}
```

`ApifyClient.create` takes the token as an explicit argument — it does **not** read `APIFY_TOKEN` (or
any other environment variable) automatically. Read it yourself if you want that, e.g.
`ApifyClient.create(System.getenv("APIFY_TOKEN"))`.

All public client types live under `com.apify.client`, split by resource into sub-packages (e.g.
`com.apify.client.run.ActorRun`, `com.apify.client.dataset.DatasetListItemsOptions`) — see
[Resources](#resources) below for the full list; [`docs/README.md`](docs/README.md#imports-and-dependencies)
enumerates the model/option-type packages. The Quick start example above is a complete, runnable
program (imports, class, `main`); every other snippet in this file, from here on, is a fragment
that assumes a configured `client` and the correct imports for the types it uses — not, by itself,
a complete program — the [resource pages](docs/README.md) show the same kind of fragment per
method. Reading items from a run's default dataset, chaining the two asynchronous calls:

```java
client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L)
    .thenCompose(run -> client.dataset(run.getDefaultDatasetId())
        .listItems(new DatasetListItemsOptions()))
    .thenAccept(items -> System.out.println("Items in this page: " + items.getCount()))
    .join();
```

The types used above — `PaginationList<T>` (root package), `DatasetListItemsOptions`
(`com.apify.client.dataset`), and the per-resource clients — are documented on the
[resource pages](docs/README.md); `ApifyApiException` (`com.apify.client.http`) is covered under
[Error handling](#error-handling) below. [`docs/examples.md`](docs/examples.md) has more fragments
in the same style (build-and-run, storages, log redirection, and more); the complete, runnable
programs live under
[`src/test/java/com/apify/client/examples/`](https://github.com/apify/apify-client-java/tree/master/src/test/java/com/apify/client/examples).

## Asynchronous API

Every network-calling method returns a
[`CompletableFuture`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
(`java.util.concurrent.CompletableFuture`) instead of blocking the calling thread on the HTTP
exchange, and every paginated collection's `iterate(...)` returns a
[`Flow.Publisher`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Flow.Publisher.html)
(`java.util.concurrent.Flow.Publisher`) — the JDK's built-in Reactive Streams type — instead of a
blocking `Iterator`, so following a large collection never blocks a thread waiting on the next
page's network round-trip.

- **Chain calls** with `thenApply`/`thenCompose`/`thenAccept` (see the dataset example above) to
  keep a pipeline fully asynchronous end to end.
- **Block for a result** with `future.join()` (unchecked) or `future.get()` (checked,
  `InterruptedException`/`ExecutionException`) when you just want a synchronous-looking call site
  (e.g. a `main` method, a test, or a simple script) — every snippet in this documentation that
  needs a value calls `.join()` for exactly that reason.
- **Consume an `iterate(...)` publisher** either by implementing `Flow.Subscriber<T>` yourself
  (`onSubscribe` → `request(n)` to pull `n` items, `onNext` per item, `onComplete`/`onError` to
  finish) for real backpressure, or with the small convenience bridge
  `com.apify.client.Publishers.collect(publisher)`, which subscribes with unbounded demand and
  returns a `CompletableFuture<List<T>>` — useful when you just want "give me everything" and don't
  need backpressure:

  ```java
  List<Actor> actors = Publishers.collect(client.actors().iterate(new ActorListOptions())).join();
  ```

A future/publisher's failure mode is unchanged from before: any `ApifyApiException`/
`ApifyTransportException` this client throws now arrives as the future's exceptional completion
(surfaced by `.join()`/`.get()` as a `CompletionException`/`ExecutionException` wrapping the
original, still-unchecked exception) instead of a direct `throw` — see
[Error handling](#error-handling) below for the full exception hierarchy, which is otherwise
unchanged.

## Configuration

Use `ApifyClient.builder()` for non-default settings:

```java
ApifyClient configured =
    ApifyClient.builder()
        .token("my-api-token")
        .baseUrl("https://api.apify.com") // /v2 is appended automatically
        .maxRetries(8)
        .minDelayBetweenRetries(Duration.ofMillis(500))
        .timeout(Duration.ofSeconds(360))
        .userAgentSuffix("MyTool/1.0")
        .build();
```

`Duration` above is `java.time.Duration`.

### `ApifyClientBuilder` reference

Every setter is optional; leaving one uncalled keeps its default. `int`/`Duration` arguments are
validated at call time (see below), not deferred to `build()`.

| Setter | Type | Default | Valid range |
|---|---|---|---|
| `token(String)` | `String` | none (unauthenticated) | any non-null string |
| `baseUrl(String)` | `String` | `https://api.apify.com` (`/v2` appended automatically) | non-null, non-blank |
| `publicBaseUrl(String)` | `String` | same as `baseUrl` | non-null, non-blank |
| `maxRetries(int)` | `int` | `8` | `>= 0` |
| `minDelayBetweenRetries(Duration)` | `Duration` | `500ms` | non-null, `>= 0` |
| `maxDelayBetweenRetries(Duration)` | `Duration` | same as `timeout` (`360s`) | non-null, `>= 0` |
| `timeout(Duration)` | `Duration` | `360s` | non-null, strictly `> 0` |
| `userAgentSuffix(String)` | `String` | none (no suffix appended) | any string |
| `httpTransport(HttpTransport)` | `HttpTransport` | `new DefaultHttpTransport()` | non-null |

A violated range throws `IllegalArgumentException` from the setter itself. `timeout` is the ceiling
each retry attempt's per-request (socket) timeout grows toward — not a wall-clock bound on the
cumulative time across all retries; the connection-establishment timeout is a separate,
transport-level setting (see `DefaultHttpTransport`'s constructors below).

`publicBaseUrl` does not affect ordinary API requests (those always use `baseUrl`); it only changes
the origin embedded in the handful of public, directly-fetchable URLs the client builds without a
request — `DatasetClient.createItemsPublicUrl(...)` and `KeyValueStoreClient.getRecordPublicUrl(...)`/
`createKeysPublicUrl(...)` (see [Storages](docs/storages.md)). Set it when the API is reached
through one origin (e.g. an internal proxy) but those public URLs must point at a different,
externally-reachable one.

### Replaceable HTTP transport

The transport is a replaceable component. The default is `DefaultHttpTransport` (backed by the JDK's
`java.net.http.HttpClient`); provide your own `HttpTransport` to share a connection pool or customize
proxy/TLS:

```java
HttpTransport transport = new DefaultHttpTransport(java.net.http.HttpClient.newHttpClient());
ApifyClient withTransport = ApifyClient.builder().token("t").httpTransport(transport).build();
```

`DefaultHttpTransport` also has a `DefaultHttpTransport(Duration connectTimeout)` constructor that
builds its own JDK `HttpClient` with a custom connection-establishment timeout (default 10s,
`DefaultHttpTransport.DEFAULT_CONNECT_TIMEOUT`) without requiring you to construct the `HttpClient`
yourself — use it when you only want to change the connect timeout and don't otherwise need to
customize the JDK client:

```java
HttpTransport transport = new DefaultHttpTransport(Duration.ofSeconds(5));
```

Cross-cutting behaviour applied to every request lives in the client, not the transport
implementation: bearer-token authentication, the mandated `User-Agent` header, and retries with
exponential backoff and jitter on `429`, `5xx` and network errors.

#### `HttpTransport` contract

A custom implementation (`com.apify.client.http.HttpTransport`) must provide both methods, both
non-blocking. `HttpRequest` and `HttpResponse<T>` below are the JDK's `java.net.http.HttpRequest`/
`java.net.http.HttpResponse<T>` (not custom client types) — same as `Duration` above:

| Method | Signature | Contract |
|---|---|---|
| `sendAsync` | `CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest request)` | Sends the single, fully-prepared request (auth, `User-Agent` and any other headers are already set by the client) and completes the future with the response with its body fully buffered as `byte[]`. Used for every non-streaming call. |
| `sendStreamingAsync` | `CompletableFuture<HttpResponse<InputStream>> sendStreamingAsync(HttpRequest request)` | Sends the request and completes the future with the response body as a live `InputStream` for incremental consumption, used by log streaming (`LogClient.stream(...)`). The *caller* (the client) is responsible for closing the returned stream; an implementation must not close it itself before completing the future. |

Both methods perform exactly one network round-trip each — no retrying, no request mutation
(retries, auth, and the `User-Agent` header are handled one layer up, by the client itself), and
neither may block the calling thread waiting on the network. A non-2xx HTTP status is **not** a
transport-level error: complete the future normally with it, as a normal `HttpResponse` carrying its
actual status code. Only fail the future for a genuine transport failure — connection refused, DNS
resolution failure, or a timeout. Any exception the future is completed exceptionally with is
translated by the client into an `ApifyTransportException`; complete exceptionally with
`com.apify.client.http.HttpTimeoutException` specifically for a timeout so
`ApifyTransportException.isTimeout()` reports it correctly. `DefaultHttpTransport` implements both
methods directly on top of the JDK `HttpClient`'s own `sendAsync`.

## Fetching single resources

Methods that fetch a single resource complete with an `Optional<T>`: a missing resource is reported
by an empty `Optional` rather than an exception.

```java
client.actor("apify/hello-world").get()
    .thenAccept(actor -> actor.ifPresent(a -> System.out.println(a.getTitle())))
    .join();
```

## Error handling

Every exception this client throws for a request/transport failure is an unchecked
`com.apify.client.http.ApifyClientException`. It has two concrete subtypes, both also in
`com.apify.client.http`. Since every network-calling method is asynchronous (see
[Asynchronous API](#asynchronous-api) above), these exceptions arrive as the returned
`CompletableFuture`'s exceptional completion rather than a direct `throw`; `.join()` re-throws the
original exception wrapped in an unchecked `CompletionException` (`.get()` wraps it in a checked
`ExecutionException` instead) — unwrap `getCause()` to recover the original
`ApifyApiException`/`ApifyTransportException`, or use `.handle(...)`/`.exceptionally(...)` to react
to it without unwrapping at all:

- `ApifyApiException` — the request reached the API, which answered with a non-success status.
- `ApifyTransportException` — the request never produced an API response at all (connection
  failure, DNS, timeout, or a local failure preparing the request/response, e.g. compression).
  `isTimeout()` reports whether the underlying cause was specifically a timeout (backed by
  `com.apify.client.http.HttpTimeoutException`, part of the `HttpTransport` contract, not any
  specific transport implementation's own exception type). Note the name collision: this is a
  distinct type from the JDK's own `java.net.http.HttpTimeoutException` (which
  `DefaultHttpTransport` catches internally and translates into this one) — use an explicit,
  fully-qualified import or a clear alias if a file needs both.

Catch `ApifyClientException` to handle both failure modes uniformly, or catch a specific subtype to
handle one of them differently. `ApifyApiException` is imported from `com.apify.client.http`:

A few methods validate their own preconditions synchronously, before ever building a request or
future, and throw a plain JDK exception directly from the call site instead, not an
`ApifyClientException` subtype (so no `.join()`/`.get()` unwrapping is involved for these):
`ApifyClient.setStatusMessage(message, options)` throws `IllegalStateException` if the
`ACTOR_RUN_ID` environment variable is not set, `ApifyClient.me()`'s limits methods (`limits()`,
`updateLimits(...)`, `monthlyUsage(...)`) throw the same `IllegalStateException` if called on a
`UserClient` that is not `me()`, and `ApifyClientBuilder`'s setters throw `IllegalArgumentException`
on an invalid configuration value (e.g. a negative retry count). These are local, no-request-sent
failures, not something the API responded with or the transport failed to deliver, which is why
they stay outside the request/transport exception hierarchy above — catch
`IllegalStateException`/`IllegalArgumentException` separately if you call any of them.

```java
try {
  client.actor("does/not-exist").update(Map.of("title", "x")).join();
} catch (CompletionException e) {
  if (e.getCause() instanceof ApifyApiException apiError) {
    System.out.println("status=" + apiError.getStatusCode() + " type=" + apiError.getType());
  } else {
    throw e;
  }
}
```

`ApifyApiException` exposes the parsed error details:

| Accessor | Type | Meaning |
|---|---|---|
| `getStatusCode()` | `int` | HTTP status code of the error response. |
| `getType()` | `String` (nullable) | Machine-readable error type (e.g. `record-not-found`). |
| `getMessage()` | `String` | Human-readable description (also `Throwable.getMessage()`). |
| `getAttempt()` | `int` | The (1-based) attempt number that produced the error. |
| `getHttpMethod()` | `String` | The request's HTTP method. |
| `getPath()` | `String` | The request's URL path. |
| `getData()` | `Map<String, Object>` (nullable, unmodifiable) | Additional structured error data, if any. |

## Versioning

The public `com.apify.client.Version` class (`import com.apify.client.Version;`) exposes two
constants:

- `Version.CLIENT_VERSION` — the semantic version of this client (`0.5.0`).
- `Version.API_SPEC_VERSION` — the version of the [Apify OpenAPI specification](https://docs.apify.com/api/openapi.json)
  (its `info.version` field) that this client's endpoints, parameters and models were last generated
  and checked against (`v2-2026-07-22T122437Z`). It is a snapshot, not a live compatibility
  guarantee: the client keeps working against newer, backward-compatible spec revisions, but a
  feature added to the API after this snapshot has no corresponding method here yet.

Changes to the public interface other than additive ones are considered breaking changes and follow
[Semantic Versioning](https://semver.org/).

### Releasing

Releases are published to Maven Central through the Sonatype Central Publisher Portal by the
manually-triggered `Publish Java client` GitHub Actions workflow. The workflow runs in the
protected `Publishing` GitHub environment and authenticates to the portal with the Maven Central
repository credentials held there, signs the artifacts with GPG, publishes the
`com.apify:apify-client` artifact, and creates a tagged GitHub release. The release version is taken
from the `<version>` in `pom.xml`.

## Scope

The client covers the documented Apify API endpoints that the JavaScript reference client exposes.

For cross-client parity, the following documented spec endpoints are intentionally **not**
implemented (the JS reference exposes none of them):

- The synchronous run endpoints (`run-sync`, `run-sync-get-dataset-items`).
- The cryptographic tools `POST /v2/tools/encode-and-sign` and `POST /v2/tools/decode-and-verify`
  (this client performs the same HMAC-SHA256 URL signing locally).
- `/v2/browser-info`.
- The keyed-`POST` create variants that duplicate the covered `PUT` writes.

## Documentation

Full documentation is in the [`docs/`](docs/README.md) directory, organized by resource:

- [Actors, versions & environment variables](docs/actors.md)
- [Builds](docs/builds.md)
- [Runs](docs/runs.md)
- [Storages (datasets, key-value stores, request queues)](docs/storages.md)
- [Tasks](docs/tasks.md)
- [Schedules](docs/schedules.md)
- [Webhooks & dispatches](docs/webhooks.md)
- [Store, users & logs](docs/misc.md)
- [Examples](docs/examples.md)

## Resources

Every resource client lives in its own sub-package of `com.apify.client`, named after the resource.
`ApifyClient` itself, its builder, the exception types, and shared value types (`PaginationList`,
`Version`, ...) stay in the root `com.apify.client` package.

| Accessor | Client | Package | Description |
|---|---|---|---|
| `actors()` / `actor(id)` | `ActorCollectionClient` / `ActorClient` | `com.apify.client.actor` | Actors |
| `builds()` / `build(id)` | `BuildCollectionClient` / `BuildClient` | `com.apify.client.build` | Actor builds |
| `runs()` / `run(id)` | `RunCollectionClient` / `RunClient` | `com.apify.client.run` | Actor runs |
| `datasets()` / `dataset(id)` | `DatasetCollectionClient` / `DatasetClient` | `com.apify.client.dataset` | Datasets |
| `keyValueStores()` / `keyValueStore(id)` | `KeyValueStoreCollectionClient` / `KeyValueStoreClient` | `com.apify.client.keyvalue` | Key-value stores |
| `requestQueues()` / `requestQueue(id)` | `RequestQueueCollectionClient` / `RequestQueueClient` | `com.apify.client.requestqueue` | Request queues |
| `tasks()` / `task(id)` | `TaskCollectionClient` / `TaskClient` | `com.apify.client.task` | Actor tasks |
| `schedules()` / `schedule(id)` | `ScheduleCollectionClient` / `ScheduleClient` | `com.apify.client.schedule` | Schedules |
| `webhooks()` / `webhook(id)` | `WebhookCollectionClient` / `WebhookClient` | `com.apify.client.webhook` | Webhooks |
| `webhookDispatches()` / `webhookDispatch(id)` | `WebhookDispatchCollectionClient` / `WebhookDispatchClient` | `com.apify.client.webhook` | Webhook dispatches |
| `store()` | `StoreCollectionClient` | `com.apify.client.store` | Apify Store |
| `me()` / `user(id)` | `UserClient` | `com.apify.client.user` | Users |
| `log(id)` | `LogClient` | `com.apify.client.log` | Build/run logs |
| `setStatusMessage(message, options)` | — (direct `ApifyClient` method, no resource client) | `com.apify.client.run` (for `SetStatusMessageOptions`) | Sets the status message of the current Actor run (see [docs/README.md](docs/README.md#setting-the-current-runs-status-message)) |

The HTTP transport contract (`HttpTransport`, `DefaultHttpTransport`) and the exceptions thrown for
transport-level failures (`ApifyTransportException`, `HttpTimeoutException`) live in
`com.apify.client.http`, alongside `ApifyApiException`.

## License

[Apache License 2.0](LICENSE).
