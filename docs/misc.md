# Store, users & logs

## Apify Store — `client.store()`

Browse public Actors in the Apify Store. `client.store()` returns a `StoreCollectionClient`.

| Method | Description |
|---|---|
| `list(StoreListOptions)` | A page of Store Actors. Completes with `PaginationList<ActorStoreListItem>`. |
| `iterate(StoreListOptions, Long chunkSize)` | A lazy `Flow.Publisher<ActorStoreListItem>` over all matches, fetching pages on demand. The options' `limit` caps the total number of Actors yielded (`null`/unset or non-positive = all); `chunkSize` is the per-request page size (`null` = server default). |

`StoreListOptions` fields: `offset` (number of Actors to skip), `limit` (maximum number of Actors to
return), `search` (full-text search query), `sortBy` (the sort field, e.g. `popularity`, `newest`),
`category` (filter Actors by category), `username` (filter Actors by owner username), `pricingModel`
(filter by pricing model: `FREE`, `FLAT_PRICE_PER_MONTH`, `PRICE_PER_DATASET_ITEM`, `PAY_PER_EVENT`),
`includeUnrunnableActors` (include Actors the current user cannot run), `allowsAgenticUsers` (only
Actors that allow agentic users), `responseFormat` (the response shape: `full` or `agent`).

```java
// Publishers.collect() drains the whole publisher; fine here since `limit` already caps it.
// To stop early without fetching further pages, subscribe with your own Flow.Subscriber instead
// (request(1) at a time, cancel() once you have enough) — see IterateStore.java in the examples.
List<ActorStoreListItem> items =
    Publishers.collect(client.store().iterate(new StoreListOptions().search("crawler").limit(20L), 10L))
        .join();
items.forEach(item -> System.out.println(item.getUsername() + "/" + item.getName()));
```

`ActorStoreListItem` fields: `getId()`, `getName()`, `getUsername()`, `getTitle()`,
`getDescription()`, `getStats()` (`ActorStats`; see [Actors](actors.md#actorclient)),
`getCurrentPricingInfo()` (`PricingInfo` — `getPricingModel()`), `getPictureUrl()`,
`getUserPictureUrl()`, `getUrl()`, `getReadmeSummary()`. Any field not covered by a typed getter is
still available via the inherited `getExtra()` (see
[the docs index](README.md#model-fields-and-unmodeled-data-getextra)).

## Users — `client.me()` / `client.user(id)`

| Method | Description |
|---|---|
| `get()` | Fetch the user. Completes with `Optional<User>` (private details for `me()` via `getExtra()`). |
| `monthlyUsage()` / `monthlyUsage(String date)` | Account monthly usage (`me()` only). `date` (any day within the target month, formatted `YYYY-MM-DD`) reports that month; `null`/empty reports the current month. Completes with `JsonNode`. |
| `limits()` | Account resource limits (`me()` only). Completes with `JsonNode`. |
| `updateLimits(Object)` | Update account limits (`me()` only). Completes with no value (`CompletableFuture<Void>`). |

The usage/limits methods are only available for `me()`; calling them on `user(id)` throws
`IllegalStateException`.

```java
Optional<User> me = client.me().get().join();
me.ifPresent(
    u -> {
      System.out.println("Account: " + u.getId());
      // email/plan/proxy/... are only populated for me() (not user(id)); use getExtra() for any
      // remaining account field not modelled directly.
      System.out.println("Email: " + u.getEmail());
    });
JsonNode usage = client.me().monthlyUsage().join();
```

`User` fields: `getId()`, `getUsername()`, `getProfile()` (`UserProfile` — `getBio()`, `getName()`,
`getPictureUrl()`, `getGithubUsername()`, `getWebsiteUrl()`, `getTwitterUsername()`, all nullable).
Only present for `me()`: `getEmail()`, `getProxy()` (`UserProxy` — `getPassword()`, `getGroups()`
as `List<ProxyGroup>`, each with `getName()`/`getDescription()`/`getAvailableCount()` as `Long`),
`getPlan()` (`UserPlan` — `getId()`, `getDescription()`, `getIsEnabled()`,
`getMonthlyBasePriceUsd()`/`getMonthlyUsageCreditsUsd()`/`getUsageDiscountPercent()`/
`getMaxMonthlyUsageUsd()`/`getMaxActorMemoryGbytes()`/`getMaxMonthlyActorComputeUnits()`/
`getMaxMonthlyResidentialProxyGbytes()`/`getMaxMonthlyProxySerps()`/
`getMaxMonthlyExternalDataTransferGbytes()` as `Double`, `getEnabledPlatformFeatures()` as
`List<String>`, `getMaxActorCount()`/`getMaxActorTaskCount()`/`getDataRetentionDays()`/
`getTeamAccountSeatCount()` as `Long`, `getAvailableProxyGroups()` as `JsonNode`,
`getSupportLevel()`, `getAvailableAddOns()` as `List<JsonNode>`), `getEffectivePlatformFeatures()`
(`JsonNode`), `getCreatedAt()` (`Instant`), `getIsPaying()` (`Boolean`). Plus `getExtra()` — a
`Map<String, Object>` carrying any account field not modelled directly.

## Logs — `client.log(id)`

Access a build's or run's log directly, or via `client.run(id).log()` / `client.build(id).log()`.

| Method | Description |
|---|---|
| `get()` / `get(LogOptions)` | The whole log as text. Completes with `Optional<String>`. |
| `stream()` / `stream(LogOptions)` | A live `InputStream` over the log (for redirection). Completes with `InputStream`. |

`LogOptions` fields: `raw(Boolean)`, `download(Boolean)`.

```java
Optional<String> log = client.log("RUN_OR_BUILD_ID").get().join();
log.ifPresent(System.out::println);
```
