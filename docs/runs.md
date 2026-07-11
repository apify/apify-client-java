# Runs

Access the run collection with `client.runs()` (or `client.actor(id).runs()` /
`client.task(id).runs()`) and a single run with `client.run(id)`.

## `RunCollectionClient`

| Method | Description |
|---|---|
| `list(ListOptions, RunListOptions)` | List runs. Returns `PaginationList<ActorRun>`. |
| `iterate(ListOptions, RunListOptions, Long chunkSize)` | Lazy `Iterator<ActorRun>` over all matching runs; the options' `limit` caps the total yielded (`null`/unset or non-positive = all), `chunkSize` sets the per-request page size (`null` = server default). |

`RunListOptions` adds `status(List<String>)` (e.g. `SUCCEEDED`, `RUNNING`; sent comma-separated) and,
for Actor/task-scoped collections, `startedAfter(String)` / `startedBefore(String)` (ISO-8601).

```java
PaginationList<ActorRun> runs = client.runs().list(
    new ListOptions().limit(10L),
    new RunListOptions().status(List.of("SUCCEEDED")));
```

## `RunClient`

| Method | Description |
|---|---|
| `get()` | Fetch the run. Returns `Optional<ActorRun>`. |
| `getWithWait(Long waitForFinishSecs)` | Fetch, optionally waiting server-side for the run to finish (clamped to the request timeout; the API caps server-side waiting at 60s). Returns `Optional<ActorRun>`. |
| `update(Object)` | Update the run. Returns `ActorRun`. |
| `delete()` | Delete the run. |
| `abort(Boolean gracefully)` | Abort the run (`null` = server default). Returns `ActorRun`. |
| `metamorph(String targetActorId, Object input, MetamorphOptions)` | Metamorph into another Actor. Returns `ActorRun`. |
| `reboot()` | Reboot the run. Returns `ActorRun`. |
| `resurrect(RunResurrectOptions)` | Resurrect a finished run. Returns `ActorRun`. |
| `charge(RunChargeOptions)` | Charge a pay-per-event run for a named event. No return value. |
| `waitForFinish(Long waitSecs)` | Poll until the run finishes (`null` waits indefinitely). Returns `ActorRun`. |
| `dataset()` / `keyValueStore()` / `requestQueue()` | Clients for the run's default storages. |
| `log()` | A `LogClient` for the run's log (see [Store, users & logs](misc.md#logs--clientlogid)). |
| `getStreamedLog()` | A live raw log `InputStream` (for log redirection). |

`getStreamedLog()` is a convenience equivalent to `run(id).log().stream(new LogOptions().raw(true))`;
use `log()` for the full log text or for non-raw/download options.

To set the current run's status message from inside an Actor, use the top-level
`client.setStatusMessage(...)` (see [the docs index](README.md#setting-single-resource-status)).

> **`lastRun()` clients are for reads.** A `RunClient` obtained via `actor(id).lastRun(...)` /
> `task(id).lastRun(...)` targets the `runs/last` path and is intended for reading (`get`,
> `dataset()`/`keyValueStore()`/`requestQueue()`, `log()`). The mutating methods (`abort`, `reboot`,
> `resurrect`, `metamorph`, `charge`, `update`, `delete`) require a concrete run and have no
> `runs/last` endpoint — resolve the id first (`lastRun(...).get()` then `client.run(id)`). The type
> exposes them uniformly (matching the reference client's shared `RunClient`), but calling them on a
> last-run client will fail server-side.

`ActorRun` fields include `getId()`, `getActId()`, `getUserId()`, `getStatus()`,
`getStatusMessage()`, `getStartedAt()`, `getFinishedAt()`, `getBuildId()`, `getDefaultDatasetId()`,
`getDefaultKeyValueStoreId()`, `getDefaultRequestQueueId()`, `getContainerUrl()`, plus `isTerminal()`.
The status is one of `READY`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMING-OUT`, `TIMED-OUT`,
`ABORTING`, `ABORTED`; `isTerminal()` is true for the finished states (`SUCCEEDED`, `FAILED`,
`TIMED-OUT`, `ABORTED`).

`RunChargeOptions` (constructed with the required event name) uses plain values: `count(Long)` and
`idempotencyKey(String)` — the latter is auto-generated when unset so a transport-retried charge is
applied at most once.

```java
client.run("RUN_ID").charge(new RunChargeOptions("my-event").count(3L));
```

`MetamorphOptions` uses plain values `build(String)` and `contentType(String)`.
`RunResurrectOptions` fields: `build`, `memoryMbytes`, `timeoutSecs`, `maxItems`,
`maxTotalChargeUsd`, `restartOnError`.
