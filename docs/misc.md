# Store, users & logs

## Apify Store — `client.store()`

Browse public Actors in the Apify Store. `client.store()` returns a `StoreCollectionClient`.

| Method | Description |
|---|---|
| `list(StoreListOptions)` | A page of Store Actors. Returns `PaginationList<ActorStoreListItem>`. |
| `iterate(StoreListOptions, Long chunkSize)` | A lazy `Iterator<ActorStoreListItem>` over all matches, fetching pages on demand. The options' `limit` caps the total number of Actors yielded (`null`/unset or non-positive = all); `chunkSize` is the per-request page size (`null` = server default). |

`StoreListOptions` fields: `offset` (number of Actors to skip), `limit` (maximum number of Actors to
return), `search` (full-text search query), `sortBy` (the sort field, e.g. `popularity`, `newest`),
`category` (filter Actors by category), `username` (filter Actors by owner username), `pricingModel`
(filter by pricing model: `FREE`, `FLAT_PRICE_PER_MONTH`, `PRICE_PER_DATASET_ITEM`, `PAY_PER_EVENT`),
`includeUnrunnableActors` (include Actors the current user cannot run), `allowsAgenticUsers` (only
Actors that allow agentic users), `responseFormat` (the response shape: `full` or `agent`).

```java
Iterator<ActorStoreListItem> it =
    client.store().iterate(new StoreListOptions().search("crawler").limit(20L), 10L);
int shown = 0;
while (shown < 5 && it.hasNext()) {
  ActorStoreListItem item = it.next();
  System.out.println(item.getUsername() + "/" + item.getName());
  shown++;
}
```

`ActorStoreListItem` fields: `getId()`, `getName()`, `getUsername()`, `getTitle()`.

## Users — `client.me()` / `client.user(id)`

| Method | Description |
|---|---|
| `get()` | Fetch the user. Returns `Optional<User>` (private details for `me()` via `getExtra()`). |
| `monthlyUsage()` / `monthlyUsage(String date)` | Account monthly usage (`me()` only). `date` (any day within the target month, formatted `YYYY-MM-DD`) reports that month; `null`/empty reports the current month. Returns `JsonNode`. |
| `limits()` | Account resource limits (`me()` only). Returns `JsonNode`. |
| `updateLimits(Object)` | Update account limits (`me()` only). No return value. |

The usage/limits methods are only available for `me()`; calling them on `user(id)` throws
`IllegalStateException`.

```java
Optional<User> me = client.me().get();
me.ifPresent(
    u -> {
      System.out.println("Account: " + u.getId());
      // Fields not modelled on User (email, plan, proxy, ...) are exposed through the untyped
      // extras map, which getExtra() returns as a Map<String, Object>.
      System.out.println("Email: " + u.getExtra().get("email"));
    });
JsonNode usage = client.me().monthlyUsage();
```

`User` fields: `getId()`, `getUsername()`, plus `getExtra()` — a `Map<String, Object>` carrying any
account fields not modelled directly (for `me()`, private details such as `email` and `plan`).

## Logs — `client.log(id)`

Access a build's or run's log directly, or via `client.run(id).log()` / `client.build(id).log()`.

| Method | Description |
|---|---|
| `get()` / `get(LogOptions)` | The whole log as text. Returns `Optional<String>`. |
| `stream()` / `stream(LogOptions)` | A live `InputStream` over the log (for redirection). |

`LogOptions` fields: `raw(Boolean)`, `download(Boolean)`.

```java
Optional<String> log = client.log("RUN_OR_BUILD_ID").get();
log.ifPresent(System.out::println);
```
