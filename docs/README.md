# Apify Java client documentation

> **Official, but experimental — AI-generated and AI-maintained.** This is an official Apify client,
> but it is experimental: it is generated and maintained by AI. Review the code before relying on it
> in production and report issues on the repository.

This directory documents the public API of the Apify Java client, organized by resource. Each page
lists the available methods with their parameters and short, runnable snippets. For an overview,
configuration, error handling and the full resource table, see the [top-level README](../README.md).

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

Snippets in these docs assume the client types are imported from `com.apify.client` (e.g.
`import com.apify.client.*;`) plus standard-library types (`java.util.List`, `java.util.Map`,
`java.util.Optional`, `java.util.stream.Stream`, `java.time.Duration`, `java.io.InputStream`).

Raw-JSON return values use Jackson's `com.fasterxml.jackson.databind.JsonNode`. Jackson is a
transitive dependency of this client, so it is already on your classpath.

## Raw JSON values

A few methods return data whose shape is not modelled by this client and is instead exposed as a
Jackson `JsonNode` (or accept an arbitrary `Object` serialized to JSON):

- Read: `me().monthlyUsage(...)`, `me().limits()`, `task(id).getInput()`,
  `build(id).getOpenApiDefinition()`, `dataset(id).getStatistics()`, and the raw request-queue
  operations (`listRequests`, `listAndLockHead`, `prolongRequestLock`, `unlockRequests`,
  `batchDeleteRequests`).
- Write: `task(id).updateInput(...)` and `me().updateLimits(...)` accept an arbitrary
  JSON-serializable value, as do definition/`update`/`create` arguments generally — a `Map`, a
  `JsonNode`, or your own POJO.

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

## Setting single-resource status

`client.setStatusMessage(String message, boolean isTerminal)` updates the status message of the
current Actor run (identified by the `ACTOR_RUN_ID` environment variable); it only works from inside
a run and throws `IllegalStateException` otherwise. Returns the updated `ActorRun`.

## Optional option fields

Option objects use fluent setters and nullable (boxed) fields; an unset field means "use the API
default". Leave a setter uncalled to omit that parameter.

```java
ActorListOptions options = new ActorListOptions().my(true).limit(10L);
PaginationList<Actor> page = client.actors().list(options);
```

## Common list options — `ListOptions`

Most `list` methods (builds, runs, tasks, schedules, webhooks, Actor versions) take the shared
`ListOptions`, which carries the standard pagination/ordering controls.

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

## Resource pages

- [Actors, versions & environment variables](actors.md)
- [Builds](builds.md)
- [Runs](runs.md)
- [Storages (datasets, key-value stores, request queues)](storages.md)
- [Tasks](tasks.md)
- [Schedules](schedules.md)
- [Webhooks & dispatches](webhooks.md)
- [Store, users & logs](misc.md)
- [Runnable examples](examples.md)
