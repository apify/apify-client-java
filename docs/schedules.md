# Schedules

> **Official but experimental — AI-generated and AI-maintained.** Review the code before relying on it in production.

Schedules automatically start Actor or task runs at specified times. Access the collection with
`client.schedules()` and a single schedule with `client.schedule(id)`.

## `ScheduleCollectionClient`

| Method | Description |
|---|---|
| `list(ListOptions)` | List schedules. Returns `PaginationList<Schedule>`. |
| `iterate(ListOptions, Long chunkSize)` | Lazy `Iterator<Schedule>` over all schedules; the options' `limit` caps the total yielded (`null`/unset or non-positive = all), `chunkSize` sets the per-request page size (`null` = server default). |
| `create(Object)` | Create a schedule from a JSON-serializable definition. Returns `Schedule`. |

The `actions` array describes what the schedule runs (e.g. a `RUN_ACTOR` action):

```java
Schedule schedule = client.schedules().create(Map.of(
    "name", "nightly",
    "cronExpression", "0 0 * * *",
    "isEnabled", true,
    "isExclusive", true,
    "actions", List.of(Map.of(
        "type", "RUN_ACTOR",
        "actorId", "apify/hello-world"))));
```

## `ScheduleClient`

| Method | Description |
|---|---|
| `get()` / `update(Object)` / `delete()` | CRUD. |
| `getLog()` | The schedule's invocation log. Returns `Optional<String>`. |

```java
Optional<Schedule> s = client.schedule("SCHEDULE_ID").get();
s.ifPresent(sched -> System.out.println(sched.getCronExpression()));
```

`Schedule` fields: `getId()`, `getUserId()`, `getName()`, `getCronExpression()`, `isEnabled()`.
