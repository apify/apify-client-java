# Tasks

Tasks are pre-configured Actor runs with stored input. Access the task collection with
`client.tasks()` and a single task with `client.task(id)`.

## `TaskCollectionClient`

| Method | Description |
|---|---|
| `list(ListOptions)` | List tasks. Completes with `PaginationList<Task>`. |
| `iterate(ListOptions, Long chunkSize)` | Lazy `Flow.Publisher<Task>` over all tasks; the options' `limit` caps the total yielded (`null`/unset or non-positive = all), `chunkSize` sets the per-request page size (`null` = server default). |
| `create(Object)` | Create a task from a JSON-serializable definition. Completes with `Task`. |

```java
Task task = client.tasks().create(Map.of(
    "actId", "apify/hello-world",
    "name", "my-task",
    "options", Map.of("build", "latest", "memoryMbytes", 256, "timeoutSecs", 60),
    "input", Map.of("message", "hello"))).join();
```

## `TaskClient`

| Method | Description |
|---|---|
| `get()` / `update(Object)` / `delete()` | CRUD. Complete with `Optional<Task>` / `Task` / no value. |
| `start(Object input, TaskStartOptions)` | Start a task run (input overrides stored input; `null` uses it). Completes with `ActorRun`. |
| `call(Object input, TaskStartOptions, Long waitSecs)` | Start and poll until finished; does **not** stream the run's log. Completes with `ActorRun`. |
| `call(Object input, TaskCallOptions, Long waitSecs)` | As above, additionally streaming the run's log for the duration of the wait by default (matching the reference client's `call` defaulting `options.log` to `'default'`). Use `TaskCallOptions.disableLogStreaming()` to opt out, or `logOptions(StreamedLogOptions)` for a custom destination. |
| `getInput()` | The stored input. Completes with `Optional<JsonNode>`. |
| `updateInput(Object)` | Replace the stored input. Completes with `JsonNode`. |
| `lastRun(String status)` / `lastRun(LastRunOptions)` | A `RunClient` for the last run (see [`LastRunOptions`](actors.md#actorclient)). |
| `runs()` | Nested run collection client. |
| `webhooks()` | Read-only nested webhook collection (`NestedWebhookCollectionClient`, `list` + `iterate`, no `create`). |

`TaskStartOptions` mirrors `ActorStartOptions` but omits the Actor-only `contentType` and
`forcePermissionLevel`: `build`, `memoryMbytes`, `timeoutSecs`, `waitForFinish`, `maxItems`,
`maxTotalChargeUsd`, `restartOnError`, `webhooks`. `TaskCallOptions` mirrors `TaskStartOptions` in
turn, but additionally omits `waitForFinish`: that field asks the API to hold the HTTP response
open server-side while the run finishes, which is redundant with (and wastes a request slot next
to) `call`'s own client-side `waitSecs` polling.

```java
ActorRun run = client.task("TASK_ID").call(null, new TaskStartOptions().memoryMbytes(512L), 120L).join();
System.out.println(run.getStatus());

// Streams the run's log to a default per-run logger for the duration of the wait.
ActorRun streamed =
    client.task("TASK_ID").call(null, new TaskCallOptions().memoryMbytes(512L), 120L).join();
```

`Task` fields: `getId()`, `getActId()`, `getUserId()`, `getName()`, `getTitle()`,
`getDescription()`, `getCreatedAt()`, `getModifiedAt()`, `getStats()` (`TaskStats`, exposing
`getTotalRuns()`), `getOptions()` (`TaskOptions`: the task's stored default run configuration —
`getBuild()` (`String`), `getTimeoutSecs()` (`Long`), `getMemoryMbytes()` (`Long`),
`getRestartOnError()` (`Boolean`)), `getInput()` (a `JsonNode` snapshot of the stored input, from
whichever response last returned this `Task` object; prefer `TaskClient.getInput()` above to fetch
it fresh on-demand), and `getActorStandby()` (`ActorStandby`, from `com.apify.client.actor`,
standby-mode configuration overrides for this task, if any). Any field not covered by a typed
getter is still available via the inherited `getExtra()` (see
[the docs index](README.md#model-fields-and-unmodeled-data-getextra)).

`ActorStandby` fields (all optional; `null` when unset): `getBuild()` (tag/number of the build
serving standby requests), `getDesiredRequestsPerActorRun()`, `getDisableStandbyFieldsOverride()`,
`getIdleTimeoutSecs()`, `getMaxRequestsPerActorRun()`, `getMemoryMbytes()`,
`getShouldPassActorInput()`.
