# Apify API client for Java

> **Official, but experimental — AI-generated and AI-maintained.** Review the code before relying
> on it in production and report issues on the repository.

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
  <version>0.4.0</version>
</dependency>
```

Gradle — ensure `mavenCentral()` is in your `repositories`, then add the dependency:

```groovy
repositories {
  mavenCentral()
}

dependencies {
  implementation 'com.apify:apify-client:0.4.0'
}
```

## Quick start

A complete, copy-pasteable first program. Add the client as a dependency in your project (see
Installation above), then run this as `HelloApify.java`:

```java
import com.apify.client.ApifyClient;
import com.apify.client.actor.ActorStartOptions;
import com.apify.client.run.ActorRun;

class HelloApify {
  public static void main(String[] args) {
    // Your API token from https://console.apify.com/settings/integrations
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));
    ActorRun run = client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L);
    System.out.println("Run " + run.getId() + " finished with status " + run.getStatus());
  }
}
```

The client is synchronous (each call blocks until the HTTP response arrives, there is no async or
reactive variant) and, once built via [`ApifyClient.create`](#quick-start) or
[`ApifyClient.builder()`](#configuration), safe for concurrent use from multiple threads: an
`ApifyClient` and the resource clients it returns carry no mutable state after construction.

The remaining snippets below are fragments that assume a configured `client` and these imports: the
client's types are organized by resource into sub-packages of `com.apify.client` (`actor`, `build`,
`run`, `dataset`, `keyvalue`, `requestqueue`, `task`, `schedule`, `webhook`, `user`, `log`, `store`,
and `http` for the replaceable transport), with `ApifyClient`, `Version`, `ApifyApiException` and the
shared list/pagination types staying in the `com.apify.client` root package — see
[`docs/`](docs/README.md) for the exact package of each type, or import every package with one
wildcard each (`import com.apify.client.*; import com.apify.client.actor.*; …`). The snippets also
use `com.fasterxml.jackson.databind.JsonNode` (from the Jackson dependency) for untyped data,
`java.time.Duration` in the configuration examples, and standard JDK types such as `java.util.Optional`
and `java.util.Map` (`import java.util.*;`).

```java
ApifyClient client = ApifyClient.create("my-api-token");

// Start an Actor and wait for it to finish. The last argument is the wait budget in seconds;
// pass a value (e.g. 120L) to bound the wait, or null to wait indefinitely.
ActorRun run = client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L);

// Read items from the run's default dataset.
PaginationList<JsonNode> items =
    client.dataset(run.getDefaultDatasetId()).listItems(new DatasetListItemsOptions());
System.out.println("Items in this page: " + items.getCount());
```

The types used above — `PaginationList<T>`, `DatasetListItemsOptions`, and the per-resource clients —
are documented on the [resource pages](docs/README.md); `ApifyApiException` is covered under
[Error handling](#error-handling) below.

`ApifyClient.create` takes the token as an explicit argument — it does **not** read `APIFY_TOKEN` (or
any other environment variable) automatically. Read it yourself if you want that, e.g.
`ApifyClient.create(System.getenv("APIFY_TOKEN"))`.

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

### Replaceable HTTP transport

The transport is a replaceable component, defined by the `com.apify.client.http.HttpClient`
interface (distinct from the JDK's own `java.net.http.HttpClient`, which the default implementation
happens to use under the hood — always refer to the JDK one by its fully-qualified name to avoid
ambiguity, as the snippet below does). The default is `DefaultApifyHttpClient`; provide your own
`HttpClient` to share a connection pool or customize proxy/TLS:

```java
HttpClient backend = new DefaultApifyHttpClient(java.net.http.HttpClient.newHttpClient());
ApifyClient withBackend = ApifyClient.builder().token("t").httpBackend(backend).build();
```

Cross-cutting behaviour applied to every request lives in the client, not the backend:
bearer-token authentication, the mandated `User-Agent` header, and retries with exponential
backoff and jitter on `429`, `5xx` and network errors.

### Logging

The client logs retry/backoff and give-up events through [SLF4J](https://www.slf4j.org/) (a facade
only — no logging implementation is bundled). Add an SLF4J binding of your choice (e.g. Logback) to
your own project's dependencies to see these logs; with no binding present, SLF4J silently discards
them, so this is safe to leave unconfigured.

### Request-body compression

Request bodies of 1024 bytes or more are compressed before sending, preferring
[brotli](https://github.com/hyperxpro/Brotli4j) (`Content-Encoding: br`) and falling back to gzip.
The brotli native codec is **not** bundled by default (it is platform-specific, and forcing every
consumer to pull down every OS/architecture's native binary is wasteful) — without it the client
transparently uses gzip, which is fully functional on its own. To opt into brotli, add both
`com.aayushatharva.brotli4j:brotli4j` and your platform's `com.aayushatharva.brotli4j:native-<os>-<arch>`
artifact (matching the brotli4j version this client compiles against — see `pom.xml`) as
dependencies of your own project.

## Fetching single resources

Methods that fetch a single resource return an `Optional<T>`: a missing resource is reported by an
empty `Optional` rather than an exception.

```java
client.actor("apify/hello-world").get().ifPresent(actor -> System.out.println(actor.getTitle()));
```

## Error handling

API failures (a request that reaches the API but returns a non-success status) are thrown as
`ApifyApiException`, an unchecked exception exposing the parsed error details:

```java
try {
  client.actor("does/not-exist").update(Map.of("title", "x"));
} catch (ApifyApiException e) {
  System.out.println("status=" + e.getStatusCode() + " type=" + e.getType());
}
```

| Accessor | Meaning |
|---|---|
| `getStatusCode()` | HTTP status code of the error response. |
| `getType()` | Machine-readable error type (e.g. `record-not-found`). |
| `getMessage()` | Human-readable description. |
| `getAttempt()` | The (1-based) attempt number that produced the error. |
| `getHttpMethod()` / `getPath()` | The request method and path. |
| `getData()` | Additional structured error data, if any. |

## Versioning

The public `com.apify.client.Version` class (`import com.apify.client.Version;`) exposes two
constants:

- `Version.CLIENT_VERSION` — the semantic version of this client (`0.4.0`).
- `Version.API_SPEC_VERSION` — the version of the [Apify OpenAPI specification](https://docs.apify.com/api/openapi.json)
  (its `info.version`, e.g. `v2-2026-07-13T092445Z`) that this release of the client was last
  checked and updated against. It is a point-in-time reference for maintainers, not a compatibility
  guarantee: the client also works against other spec versions, since the Apify API is additive and
  backwards-compatible in practice.

Changes to the public interface other than additive ones are considered breaking changes and follow
[Semantic Versioning](https://semver.org/). See [`CHANGELOG.md`](CHANGELOG.md) for the list of
changes in each release, including breaking ones (e.g. `0.4.0`'s package reorganization).

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

Each resource's classes live in their own sub-package of `com.apify.client` (see the Package
column); `ApifyClient` itself, and the shared list/pagination types, stay in the root
`com.apify.client` package.

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

## License

[Apache License 2.0](LICENSE).
