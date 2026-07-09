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

Maven:

```xml
<dependency>
  <groupId>com.apify</groupId>
  <artifactId>apify-client</artifactId>
  <version>0.2.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'com.apify:apify-client:0.2.0'
```

## Quick start

```java
ApifyClient client = ApifyClient.create("my-api-token");

// Start an Actor and wait for it to finish. The last argument is the wait budget in seconds;
// pass a value (e.g. 120L) to bound the wait, or null to wait indefinitely.
ActorRun run = client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L);

// Read items from the run's default dataset.
PaginationList<JsonNode> items =
    client.dataset(run.getDefaultDatasetId()).listItems(new DatasetListItemsOptions());
System.out.println("Item count: " + items.getCount());
```

All public client types live in the `com.apify.client` package (e.g. `import com.apify.client.*;`).
The snippets also use `com.fasterxml.jackson.databind.JsonNode` (from the Jackson dependency) for
untyped data, `java.time.Duration` in the configuration examples, and standard JDK types such as
`java.util.Optional` and `java.util.Map` (`import java.util.*;`).

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

The transport is a replaceable component. The default is `DefaultHttpBackend` (backed by the JDK's
`java.net.http.HttpClient`); provide your own `HttpBackend` to share a connection pool or customize
proxy/TLS:

```java
HttpBackend backend = new DefaultHttpBackend(java.net.http.HttpClient.newHttpClient());
ApifyClient withBackend = ApifyClient.builder().token("t").httpBackend(backend).build();
```

Cross-cutting behaviour applied to every request lives in the client, not the backend:
bearer-token authentication, the mandated `User-Agent` header, and retries with exponential
backoff and jitter on `429`, `5xx` and network errors.

## Fetching single resources

Methods that fetch a single resource return an `Optional<T>`: a missing resource is reported by an
empty `Optional` rather than an exception.

```java
Optional<Actor> maybeActor = client.actor("apify/hello-world").get();
if (maybeActor.isPresent()) {
  System.out.println(maybeActor.get().getTitle());
}
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

- `Version.CLIENT_VERSION` — the semantic version of this client (`0.2.0`).
- `Version.API_SPEC_VERSION` — the Apify OpenAPI specification version this client was verified
  against (`v2-2026-07-07T132551Z`).

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
- [Runnable examples](docs/examples.md)

## Resources

| Accessor | Client | Description |
|---|---|---|
| `actors()` / `actor(id)` | `ActorCollectionClient` / `ActorClient` | Actors |
| `builds()` / `build(id)` | `BuildCollectionClient` / `BuildClient` | Actor builds |
| `runs()` / `run(id)` | `RunCollectionClient` / `RunClient` | Actor runs |
| `datasets()` / `dataset(id)` | `DatasetCollectionClient` / `DatasetClient` | Datasets |
| `keyValueStores()` / `keyValueStore(id)` | `KeyValueStoreCollectionClient` / `KeyValueStoreClient` | Key-value stores |
| `requestQueues()` / `requestQueue(id)` | `RequestQueueCollectionClient` / `RequestQueueClient` | Request queues |
| `tasks()` / `task(id)` | `TaskCollectionClient` / `TaskClient` | Actor tasks |
| `schedules()` / `schedule(id)` | `ScheduleCollectionClient` / `ScheduleClient` | Schedules |
| `webhooks()` / `webhook(id)` | `WebhookCollectionClient` / `WebhookClient` | Webhooks |
| `webhookDispatches()` / `webhookDispatch(id)` | `WebhookDispatchCollectionClient` / `WebhookDispatchClient` | Webhook dispatches |
| `store()` | `StoreCollectionClient` | Apify Store |
| `me()` / `user(id)` | `UserClient` | Users |
| `log(id)` | `LogClient` | Build/run logs |

## License

[Apache License 2.0](LICENSE).
