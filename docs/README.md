# Apify Java client documentation

This directory documents the public API of the Apify Java client, organized by resource. Each page
lists the available methods with their parameters and short snippets. The snippets are code
fragments that assume a configured `client` and the imports listed below, not standalone `main`
programs; for complete, runnable programs see [examples.md](examples.md) and
[`src/test/java/com/apify/client/examples/`](https://github.com/apify/apify-client-java/tree/master/src/test/java/com/apify/client/examples). For an
overview, configuration, error handling and the full resource table, see the
[top-level README](../README.md).

All snippets assume a configured client:

```java
ApifyClient client = ApifyClient.create("my-api-token");
```

`ApifyClient.create` takes the token as an explicit argument — it does not read `APIFY_TOKEN`
automatically. Use `ApifyClient.builder()` for non-default settings (base URL, retries, timeout,
user-agent suffix, custom HTTP backend).

Get your API token from the
[Apify Console → Settings → API & Integrations](https://console.apify.com/settings/integrations).

Methods that fetch a single resource return an `Optional<T>`: a missing resource is reported by an
empty `Optional` rather than an exception. API failures are thrown as `ApifyApiException` (see
[error handling](../README.md#error-handling)).

## Imports and dependencies

Client types are **not** all in one package: `import com.apify.client.*;` only resolves
`ApifyClient`, its builder, and the shared kernel types below — a wildcard import does not reach
into sub-packages in Java. Every resource has its own sub-package for its client(s), model(s) and
option types, so import each resource you use from its own package:

| Package | Contains |
|---|---|
| `com.apify.client` (root) | `ApifyClient`, `ApifyClientBuilder`, `Version`, `PaginationList<T>`, `ListOptions`, `StorageListOptions`, `ApifyResource` |
| `com.apify.client.http` | `ApifyClientException`, `ApifyApiException`, `ApifyTransportException`, `HttpTransport`, `DefaultHttpTransport`, `HttpTimeoutException` |
| `com.apify.client.actor` | `Actor`, `ActorClient`, `ActorListOptions`, `ActorStartOptions`, `ActorBuildOptions`, `ActorVersion`, `ActorEnvVar`, ... |
| `com.apify.client.build` | `Build`, `BuildClient`, `BuildCollectionClient` |
| `com.apify.client.run` | `ActorRun`, `RunClient`, `RunListOptions`, `LastRunOptions`, `MetamorphOptions`, `RunChargeOptions`, `RunResurrectOptions`, `SetStatusMessageOptions` |
| `com.apify.client.dataset` | `Dataset`, `DatasetClient`, `DatasetListItemsOptions`, `DatasetDownloadOptions` |
| `com.apify.client.keyvalue` | `KeyValueStore`, `KeyValueStoreClient`, `KeyValueStoreRecord`, `GetRecordOptions`, `SetRecordOptions`, `ListKeysOptions` |
| `com.apify.client.requestqueue` | `RequestQueue`, `RequestQueueClient`, `RequestQueueRequest`, `ListRequestsOptions`, `BatchAddRequestsOptions` |
| `com.apify.client.task` | `Task`, `TaskClient`, `TaskStartOptions`, `ValidateInputOptions` |
| `com.apify.client.schedule` | `Schedule`, `ScheduleClient` |
| `com.apify.client.webhook` | `Webhook`, `WebhookClient`, `WebhookDispatch`, `WebhookDispatchClient` |
| `com.apify.client.user` | `User`, `UserClient` |
| `com.apify.client.store` | `ActorStoreListItem`, `StoreCollectionClient`, `StoreListOptions` |
| `com.apify.client.log` | `LogClient`, `LogOptions`, `StreamedLog`, `StreamedLogOptions` |

For example, the [top-level README's dataset snippet](../README.md#quick-start) needs
`com.apify.client.ApifyClient`, `com.apify.client.PaginationList`,
`com.apify.client.dataset.DatasetListItemsOptions`, `com.apify.client.run.ActorRun`,
`com.apify.client.actor.ActorStartOptions` and `com.fasterxml.jackson.databind.JsonNode` — five
different packages for one six-line snippet.

Snippets in these docs also assume the standard-library types they use are imported
(`java.util.List`, `java.util.ArrayList`, `java.util.Map`, `java.util.Optional`,
`java.util.Iterator`, `java.util.UUID`, `java.util.function.Consumer`, `java.time.Duration`,
`java.io.InputStream`).

Raw-JSON return values use Jackson's `com.fasterxml.jackson.databind.JsonNode`. Jackson is a
transitive dependency of this client, so it is already on your classpath.

## Raw JSON values

A few methods return data whose shape is not modelled by this client and is instead exposed as a
Jackson `JsonNode` (or accept an arbitrary `Object` serialized to JSON):

- Read, returning a required `JsonNode` (never absent): `me().monthlyUsage(...)`, `me().limits()`,
  and the raw request-queue operations `listRequests`, `listAndLockHead`, `prolongRequestLock`,
  `unlockRequests`, `batchDeleteRequests`.
- Read, returning `Optional<JsonNode>` (empty when the underlying resource has none): `dataset(id).getStatistics()`,
  `task(id).getInput()`, `build(id).getOpenApiDefinition()`.
- Write: `task(id).updateInput(...)` (itself returning a required `JsonNode`, the updated input)
  and `me().updateLimits(...)` accept an arbitrary JSON-serializable value, as do
  definition/`update`/`create` arguments generally — a `Map`, a `JsonNode`, or your own POJO.

Navigate a `JsonNode` with `node.get("field")`, `node.path("a").asText()`, etc.

## Model fields and unmodeled data (`getExtra`)

Response models expose the commonly-used fields as typed getters. The API returns more fields than
are modelled; every model also carries a `getExtra()` map (`Map<String, Object>`) holding any field
not mapped to a typed getter, so nothing the API returns is lost. For example a `Schedule`'s
`actions`/`isExclusive`, or a `me()` `User`'s private account details (email, plan, proxy settings,
…), are available via `getExtra()`.

```java
Schedule schedule = client.schedule("SCHEDULE_ID").get().orElseThrow();
Object actions = schedule.getExtra().get("actions");
```

Some models expose only their most commonly used fields as typed getters and leave more of the
API response in `getExtra()` than others — `Schedule`, `Webhook`, `Task` and `ActorRun` in
particular currently model a subset of what the API returns (e.g. a `Schedule`'s `title`,
`timezone`, `notifications`; a `Webhook`'s `condition`, `payloadTemplate`, `stats`; an `ActorRun`'s
`usage`, `options`, `pricingInfo`). Read those fields via `getExtra()` until a typed getter is
added; the raw JSON key is unchanged either way.

## Setting the current run's status message

`client.setStatusMessage(String message, SetStatusMessageOptions)` (import from
`com.apify.client.run`) updates the status message of the current Actor run (identified by the
`ACTOR_RUN_ID` environment variable); it only works from inside a run and throws
`IllegalStateException` otherwise. `SetStatusMessageOptions.isStatusMessageTerminal(boolean)` marks
the message as final so it won't be overwritten. Returns the updated `ActorRun`.

```java
client.setStatusMessage("half way there", new SetStatusMessageOptions().isStatusMessageTerminal(false));
```

## Optional option fields

Option objects use fluent setters and nullable (boxed) fields; an unset field means "use the API
default". Leave a setter uncalled to omit that parameter.

```java
ActorListOptions options = new ActorListOptions().my(true).limit(10L);
PaginationList<Actor> page = client.actors().list(options);
```

## Common list options — `ListOptions`

Most `list` methods (builds, tasks, schedules, webhooks, Actor versions) take the shared
`ListOptions`, which carries the standard pagination/ordering controls. Runs additionally take a
`RunListOptions` status filter — `runs().list(ListOptions, RunListOptions)`; see [Runs](runs.md).

| Method | Type | Meaning |
|---|---|---|
| `offset(Long)` | `Long` | Number of items to skip from the start of the list. |
| `limit(Long)` | `Long` | Maximum number of items to return. |
| `desc(Boolean)` | `Boolean` | If `true`, return items newest-first. |

```java
PaginationList<Build> builds = client.builds().list(new ListOptions().limit(50L).desc(true));
```

## Pagination — `PaginationList<T>`

`list` methods return a `PaginationList<T>` page with `getTotal()`, `getOffset()`, `getLimit()`,
`getCount()`, `isDesc()` and `getItems()`. Within-storage listers (`listKeys`, `listHead`) return
their own page/head containers instead.

## Iteration — `iterate` / `iterateItems` / `iterateKeys`

Each paginated collection also offers a lazy `Iterator` that fetches pages on demand: `iterate(...)`
on the collection clients, `DatasetClient.iterateItems(...)`, and `KeyValueStoreClient.iterateKeys(...)`
(request-queue requests use `RequestQueueClient.paginateRequests(...)`). The options' `limit` caps the
**total** number of items yielded; `null`/unset — or a non-positive value such as `0` — means no cap,
so every item is yielded. (This differs from `list(...)`, which sends `limit=0` to the server
verbatim rather than treating it as unbounded — the iteration behavior matches the reference JS
client.) The per-request page size is an optional
trailing `chunkSize` argument: the per-resource tables below show the `chunkSize` form, and each
iterator also has an overload that omits it (using the server's default page size). The page size
does not change which items a collection iterator yields; note the one exception in
[Storages](storages.md) — `iterateItems` combined with server-side item filters, where the page size
can affect the result.

## Resource pages

- [Actors, versions & environment variables](actors.md)
- [Builds](builds.md)
- [Runs](runs.md)
- [Storages (datasets, key-value stores, request queues)](storages.md)
- [Tasks](tasks.md)
- [Schedules](schedules.md)
- [Webhooks & dispatches](webhooks.md)
- [Store, users & logs](misc.md)
- [Examples](examples.md)
