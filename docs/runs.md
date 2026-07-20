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
| `getStreamedLog()` | A `StreamedLog` that redirects the live log to a default per-run logger. |
| `getStreamedLog(StreamedLogOptions)` | As above, with a custom destination / options. |

### Streamed log redirection

`getStreamedLog()` returns a `StreamedLog`: a helper that follows the run's live raw log in a
background thread and redirects each complete, timestamped message to a destination. It is
`AutoCloseable` — call `start()` to begin redirection and `stop()` (or `close()`, via
try-with-resources) to end it.

With no options, messages go to an SLF4J `Logger` at `INFO` level, prefixed with the
Actor name and run id (looked up automatically). `StreamedLogOptions` customizes it:

- `toLog(Consumer<String>)` — send each complete message to your own consumer instead of the
  default logger.
- `prefix(String)` — override the auto-built prefix used by the default logger.
- `fromStart(boolean)` — when `false`, skip log lines produced before redirection started
  (default `true`).

```java
RunClient runClient = client.run("RUN_ID");
List<String> collected = new ArrayList<>();
try (StreamedLog streamedLog =
    runClient.getStreamedLog(new StreamedLogOptions().toLog(collected::add).fromStart(true))) {
  streamedLog.start();
  runClient.waitForFinish(120L);
}
```

For raw stream access without redirection, use `log().stream(new LogOptions().raw(true))`; use
`log()` for the full log text or for non-raw/download options.

To set the current run's status message from inside an Actor, use the top-level
`client.setStatusMessage(...)` (see [the docs index](README.md#setting-the-current-runs-status-message)).
To update the status message of a run other than the current one, call `update()` on its
`RunClient` directly:

```java
client.run("RUN_ID").update(Map.of("statusMessage", "half way there", "isStatusMessageTerminal", false));
```

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

`ActorRun` additionally exposes: `getGeneralAccess()` (`String`; who can access the run without
owning it, e.g. `"ANYONE_WITH_ID_CAN_READ"`, `"RESTRICTED"`, or `null` to follow the account/Actor
default), `getChargedEventCounts()` (`Map<String, Long>`; per-event-type charge counts for
pay-per-event Actors, `null` otherwise), `getPricingInfo()` (`JsonNode`; shape depends on the
Actor's pricing model), `getUsage()` / `getUsageUsd()` (`ActorRunUsage`; per-billable-unit resource
consumption, the latter as its USD cost), `getStats()` (`ActorRunStats`; runtime metrics like
`getDurationMillis()`, `getMemAvgBytes()`, `getCpuAvgUsage()`), `getOptions()` (`ActorRunOptions`;
the run configuration actually applied, which may differ from what was requested), and `getMeta()`
(`ActorRunMeta`; `getOrigin()`, `getClientIp()`, `getUserAgent()` — how the run was initiated).

`RunChargeOptions` (constructed with the required event name) uses plain values: `count(Long)` and
`idempotencyKey(String)` — the latter is auto-generated when unset so a transport-retried charge is
applied at most once.

```java
client.run("RUN_ID").charge(new RunChargeOptions("my-event").count(3L));
```

`MetamorphOptions` uses plain values `build(String)` and `contentType(String)`.
`RunResurrectOptions` fields: `build`, `memoryMbytes`, `timeoutSecs`, `maxItems`,
`maxTotalChargeUsd`, `restartOnError`.
