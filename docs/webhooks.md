# Webhooks & dispatches

> **Official but experimental — AI-generated and AI-maintained.** Review the code before relying on it in production.

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

`Webhook` fields: `getId()`, `getUserId()`, `getRequestUrl()`, `getEventTypes()`.

## `WebhookDispatchCollectionClient` and `WebhookDispatchClient`

| Method | Description |
|---|---|
| `webhookDispatches().list(ListOptions)` | List dispatches. Returns `PaginationList<WebhookDispatch>`. |
| `webhookDispatches().iterate(ListOptions, Long chunkSize)` | Lazy `Iterator<WebhookDispatch>` over all dispatches; the options' `limit` caps the total yielded (`null`/unset or non-positive = all), `chunkSize` sets the per-request page size (`null` = server default). |
| `webhookDispatch(id).get()` | Fetch a dispatch. Returns `Optional<WebhookDispatch>`. |

`WebhookDispatch` fields: `getId()`, `getWebhookId()`.
