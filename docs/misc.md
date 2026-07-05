# Store, users & logs

## Apify Store — `client.store()`

Browse public Actors in the Apify Store.

| Method | Description |
|---|---|
| `list(StoreListOptions)` | A page of Store Actors. Returns `PaginationList<ActorStoreListItem>`. |
| `iterate(StoreListOptions)` | A lazy `Iterator<ActorStoreListItem>` fetching pages on demand. |

`StoreListOptions` fields: `offset`, `limit`, `search`, `sortBy`, `category`, `username`,
`pricingModel` (`FREE`, `FLAT_PRICE_PER_MONTH`, `PRICE_PER_DATASET_ITEM`, `PAY_PER_EVENT`),
`includeUnrunnableActors`, `allowsAgenticUsers`, `responseFormat` (`full`, `agent`).

```java
Iterator<ActorStoreListItem> it = client.store().iterate(new StoreListOptions().search("crawler").limit(20L));
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
| `monthlyUsage()` / `monthlyUsage(String date)` | Account monthly usage (`me()` only). Returns `JsonNode`. |
| `limits()` | Account resource limits (`me()` only). Returns `JsonNode`. |
| `updateLimits(Object)` | Update account limits (`me()` only). No return value. |

The usage/limits methods are only available for `me()`; calling them on `user(id)` throws
`IllegalStateException`.

```java
Optional<User> me = client.me().get();
me.ifPresent(u -> System.out.println("Account: " + u.getId()));
JsonNode usage = client.me().monthlyUsage();
```

`User` fields: `getId()`, `getUsername()`, plus `getExtra()`.

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
