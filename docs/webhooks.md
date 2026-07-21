# Webhooks & dispatches

Webhooks notify an external service when specific events occur. Access the collection with
`client.webhooks()` and a single webhook with `client.webhook(id)`. Dispatches (individual
invocations) are available account-wide via `client.webhookDispatches()` /
`client.webhookDispatch(id)` and per-webhook via `client.webhook(id).dispatches()`.

## `WebhookCollectionClient` — `client.webhooks()`

The account-wide collection supports both listing and creation.

| Method | Description |
|---|---|
| `list(ListOptions)` | List webhooks. Returns `PaginationList<Webhook>`. |
| `iterate(ListOptions, Long chunkSize)` | Lazy `Iterator<Webhook>` over all webhooks; the options' `limit` caps the total yielded (`null`/unset or non-positive = all), `chunkSize` sets the per-request page size (`null` = server default). Also available on the read-only nested collections. |
| `create(Object)` | Create a webhook. Returns `Webhook`. |

### Nested webhook collections (read-only)

`client.actor(id).webhooks()` and `client.task(id).webhooks()` return a
`NestedWebhookCollectionClient`. The Apify API only supports **reading** webhooks on those nested
paths (`GET /v2/actors/{id}/webhooks`, `GET /v2/actor-tasks/{id}/webhooks`), so this read-only type
exposes `list(ListOptions)` and `iterate(ListOptions, Long chunkSize)` — it has no `create(...)`. To
create a webhook targeting a specific Actor or task, use `client.webhooks().create(...)` and set the
Actor/task in the webhook's `condition`.

A webhook definition supplies `eventTypes` (a list of event-type strings such as
`ACTOR.RUN.SUCCEEDED`, `ACTOR.RUN.FAILED`, `ACTOR.RUN.ABORTED`, `ACTOR.RUN.TIMED_OUT`,
`ACTOR.RUN.CREATED`, `ACTOR.BUILD.SUCCEEDED`, …), a `condition` and a `requestUrl`. The definition is
a plain JSON-serializable value (e.g. a `Map`); this client does not wrap it in a typed enum:

```java
Webhook webhook = client.webhooks().create(Map.of(
    "isAdHoc", false,
    "eventTypes", List.of("ACTOR.RUN.SUCCEEDED", "ACTOR.RUN.FAILED"),
    "condition", Map.of("actorId", "apify/hello-world"),
    "requestUrl", "https://example.com/webhook"));
```

## `WebhookClient`

| Method | Description |
|---|---|
| `get()` / `update(Object)` / `delete()` | CRUD. |
| `test()` | Dispatch the webhook immediately. Returns `WebhookDispatch`. |
| `dispatches()` | This webhook's dispatch collection. |

```java
WebhookDispatch dispatch = client.webhook("WEBHOOK_ID").test();
System.out.println(dispatch.getId());
```

`Webhook` fields: `getId()`, `getUserId()`, `getRequestUrl()`, `getEventTypes()`, `getCreatedAt()`
/ `getModifiedAt()` (`Instant`), `isAdHoc()` (a one-off webhook attached to a single run, e.g. via
`ActorStartOptions.webhooks(...)`, rather than a persistent account-level webhook),
`getShouldInterpolateStrings()` (`Boolean`; whether `{{...}}` placeholders in `getPayloadTemplate()`/
`getHeadersTemplate()` are interpolated with values from the triggering event before dispatch),
`getCondition()` (`JsonNode`; one of an Actor ID, a task ID or a specific run ID, depending on how
the webhook was configured), `isIgnoreSslErrors()`, `isDoNotRetry()`, `getPayloadTemplate()`,
`getHeadersTemplate()`, `getDescription()`, `getLastDispatch()` (`WebhookLastDispatch`, nullable —
a summary of the most recent dispatch: `getStatus()` one of `"ACTIVE"`/`"SUCCEEDED"`/`"FAILED"`,
`getFinishedAt()`/`getRemovedAt()` as `Instant`), and `getStats()` (`WebhookStats`, exposing
`getTotalDispatches()`). Any field not covered by a typed getter is still available via the
inherited `getExtra()` (see [the docs index](README.md#model-fields-and-unmodeled-data-getextra)).

## `WebhookDispatchCollectionClient` and `WebhookDispatchClient`

| Method | Description |
|---|---|
| `webhookDispatches().list(ListOptions)` | List dispatches. Returns `PaginationList<WebhookDispatch>`. |
| `webhookDispatches().iterate(ListOptions, Long chunkSize)` | Lazy `Iterator<WebhookDispatch>` over all dispatches; the options' `limit` caps the total yielded (`null`/unset or non-positive = all), `chunkSize` sets the per-request page size (`null` = server default). |
| `webhookDispatch(id).get()` | Fetch a dispatch. Returns `Optional<WebhookDispatch>`. |

`WebhookDispatch` fields: `getId()`, `getUserId()`, `getWebhookId()`, `getCreatedAt()` (`Instant`),
`getStatus()` (one of `"ACTIVE"`/`"SUCCEEDED"`/`"FAILED"`), `getEventType()` (the event that
triggered the dispatch), `getCalls()` (unmodifiable `List<WebhookDispatchCall>` — each exposing
`getStartedAt()`/`getFinishedAt()` as `Instant`, `getErrorMessage()`, `getResponseStatus()`
(`Integer`), `getResponseBody()`), `getWebhook()` (`WebhookDispatchWebhookInfo`, nullable — a
`getRequestUrl()`/`isAdHoc()` summary of the webhook that produced the dispatch), and
`getEventData()` (`WebhookDispatchEventData`, nullable — `getActorRunId()`/`getActorId()`/
`getActorTaskId()`/`getActorBuildId()`, whichever apply to the triggering event). Any field not
covered by a typed getter is still available via the inherited `getExtra()` (see
[the docs index](README.md#model-fields-and-unmodeled-data-getextra)).
