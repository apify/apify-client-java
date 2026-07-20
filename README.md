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

After adding the dependency (above), create an `ApifyClient`, then drill down into a resource:

```java
import com.apify.client.ApifyClient;
import com.apify.client.run.ActorRun;
import com.apify.client.actor.ActorStartOptions;

class HelloApify {
  public static void main(String[] args) {
    // Your API token from https://console.apify.com/settings/integrations
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));
    ActorRun run = client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L);
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
enumerates the model/option-type packages. The remaining snippets in this file (like the Quick
start one above) are fragments that assume a configured `client` and the correct imports for the
types they use — they are not, by themselves, complete programs; the [resource pages](docs/README.md)
show the same kind of fragment per method. Reading items from a run's default dataset:

```java
ActorRun run = client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L);
PaginationList<JsonNode> items =
    client.dataset(run.getDefaultDatasetId()).listItems(new DatasetListItemsOptions());
System.out.println("Items in this page: " + items.getCount());
```

The types used above — `PaginationList<T>` (root package), `DatasetListItemsOptions`
(`com.apify.client.dataset`), and the per-resource clients — are documented on the
[resource pages](docs/README.md); `ApifyApiException` (`com.apify.client.http`) is covered under
[Error handling](#error-handling) below. [`docs/examples.md`](docs/examples.md) has complete,
runnable programs (build-and-run, storages, log redirection, and more).

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

The transport is a replaceable component. The default is `DefaultHttpTransport` (backed by the JDK's
`java.net.http.HttpClient`); provide your own `HttpTransport` to share a connection pool or customize
proxy/TLS:

```java
HttpTransport transport = new DefaultHttpTransport(java.net.http.HttpClient.newHttpClient());
ApifyClient withTransport = ApifyClient.builder().token("t").httpTransport(transport).build();
```

Cross-cutting behaviour applied to every request lives in the client, not the transport
implementation: bearer-token authentication, the mandated `User-Agent` header, and retries with
exponential backoff and jitter on `429`, `5xx` and network errors.

## Fetching single resources

Methods that fetch a single resource return an `Optional<T>`: a missing resource is reported by an
empty `Optional` rather than an exception.

```java
client.actor("apify/hello-world").get().ifPresent(actor -> System.out.println(actor.getTitle()));
```

## Error handling

Every exception this client throws is an unchecked `com.apify.client.http.ApifyClientException`.
It has two concrete subtypes, both also in `com.apify.client.http`:

- `ApifyApiException` — the request reached the API, which answered with a non-success status.
- `ApifyTransportException` — the request never produced an API response at all (connection
  failure, DNS, timeout, or a local failure preparing the request/response, e.g. compression).
  `isTimeout()` reports whether the underlying cause was specifically a timeout (backed by
  `HttpTimeoutException`, part of the `HttpTransport` contract, not any specific transport
  implementation's own exception type).

Catch `ApifyClientException` to handle both failure modes uniformly, or catch a specific subtype to
handle one of them differently. `ApifyApiException` is imported from `com.apify.client.http`:

```java
try {
  client.actor("does/not-exist").update(Map.of("title", "x"));
} catch (ApifyApiException e) {
  System.out.println("status=" + e.getStatusCode() + " type=" + e.getType());
}
```

`ApifyApiException` exposes the parsed error details:

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
  (its `info.version` field) that this client's endpoints, parameters and models were last generated
  and checked against (`v2-2026-07-20T094852Z`). It is a snapshot, not a live compatibility
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

The HTTP transport contract (`HttpTransport`, `DefaultHttpTransport`) and the exceptions thrown for
transport-level failures (`ApifyTransportException`, `HttpTimeoutException`) live in
`com.apify.client.http`, alongside `ApifyApiException`.

## License

[Apache License 2.0](LICENSE).
