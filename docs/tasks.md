# Tasks

> **Official, but experimental — AI-generated and AI-maintained.** Review the code before relying
> on it in production and report issues on the repository.

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
| `call(Object input, TaskStartOptions, Long waitSecs)` | Start and poll until finished. Returns `ActorRun`. |
| `getInput()` | The stored input. Returns `Optional<JsonNode>`. |
| `updateInput(Object)` | Replace the stored input. Returns `JsonNode`. |
| `lastRun(String status)` / `lastRun(LastRunOptions)` | A `RunClient` for the last run (see [`LastRunOptions`](actors.md#actorclient)). |
| `runs()` | Nested run collection client. |
| `webhooks()` | Read-only nested webhook collection (`NestedWebhookCollectionClient`, `list` + `iterate`, no `create`). |

`TaskStartOptions` mirrors `ActorStartOptions` but omits the Actor-only `contentType` and
`forcePermissionLevel`: `build`, `memoryMbytes`, `timeoutSecs`, `waitForFinish`, `maxItems`,
`maxTotalChargeUsd`, `restartOnError`, `webhooks`.

```java
ActorRun run = client.task("TASK_ID").call(null, new TaskStartOptions().memoryMbytes(512L), 120L);
System.out.println(run.getStatus());
```

`Task` fields: `getId()`, `getActId()`, `getUserId()`, `getName()`, `getTitle()`, `getCreatedAt()`,
`getModifiedAt()`.
