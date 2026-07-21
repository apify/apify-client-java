# Tasks

Tasks are pre-configured Actor runs with stored input. Access the task collection with
`client.tasks()` and a single task with `client.task(id)`.

## `TaskCollectionClient`

| Method | Description |
|---|---|
| `list(ListOptions)` | List tasks. Returns `PaginationList<Task>`. |
| `iterate(ListOptions, Long chunkSize)` | Lazy `Iterator<Task>` over all tasks; the options' `limit` caps the total yielded (`null`/unset or non-positive = all), `chunkSize` sets the per-request page size (`null` = server default). |
| `create(Object)` | Create a task from a JSON-serializable definition. Returns `Task`. |

```java
Task task = client.tasks().create(Map.of(
    "actId", "apify/hello-world",
    "name", "my-task",
    "options", Map.of("build", "latest", "memoryMbytes", 256, "timeoutSecs", 60),
    "input", Map.of("message", "hello")));
```

## `TaskClient`

| Method | Description |
|---|---|
| `get()` / `update(Object)` / `delete()` | CRUD. Return `Optional<Task>` / `Task` / `void`. |
| `start(Object input, TaskStartOptions)` | Start a task run (input overrides stored input; `null` uses it). Returns `ActorRun`. |
| `call(Object input, TaskStartOptions, Long waitSecs)` | Start and poll until finished; does **not** stream the run's log. Returns `ActorRun`. |
| `call(Object input, TaskCallOptions, Long waitSecs)` | As above, additionally streaming the run's log for the duration of the wait by default (matching the reference client's `call` defaulting `options.log` to `'default'`). Use `TaskCallOptions.disableLogStreaming()` to opt out, or `logOptions(StreamedLogOptions)` for a custom destination. |
| `getInput()` | The stored input. Returns `Optional<JsonNode>`. |
| `updateInput(Object)` | Replace the stored input. Returns `JsonNode`. |
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
ActorRun run = client.task("TASK_ID").call(null, new TaskStartOptions().memoryMbytes(512L), 120L);
System.out.println(run.getStatus());

// Streams the run's log to a default per-run logger for the duration of the wait.
ActorRun streamed = client.task("TASK_ID").call(null, new TaskCallOptions().memoryMbytes(512L), 120L);
```

`Task` fields: `getId()`, `getActId()`, `getUserId()`, `getName()`, `getTitle()`,
`getDescription()`, `getCreatedAt()`, `getModifiedAt()`, `getStats()` (`TaskStats`, exposing
`getTotalRuns()`), `getOptions()` (`TaskOptions`: the task's stored default run configuration —
`getBuild()`, `getTimeoutSecs()`, `getMemoryMbytes()`, `getRestartOnError()`), `getInput()` (a
`JsonNode` snapshot of the stored input, from whichever response last returned this `Task` object;
prefer `TaskClient.getInput()` above to fetch it fresh on-demand), and `getActorStandby()`
(`ActorStandby`, standby-mode configuration overrides for this task, if any). Any field not covered
by a typed getter is still available via the inherited `getExtra()` (see
[the docs index](README.md#model-fields-and-unmodeled-data-getextra)).
