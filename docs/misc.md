# Store, users & logs

## Apify Store: `client.store()`

Browse public Actors in the Apify Store. `client.store()` returns a `StoreCollectionClient`.

| Method | Description |
|---|---|
| `list(StoreListOptions)` | A page of Store Actors. Completes with `PaginationList<ActorStoreListItem>`. |
| `iterate(StoreListOptions, Long chunkSize)` | A lazy `Flow.Publisher<ActorStoreListItem>` over all matches, fetching pages on demand. The options' `limit` caps the total number of rows read, which are the Actors yielded unless the API drops entries after counting them (`null`/unset or non-positive = all); `chunkSize` is the per-request page size (`null` = server default). With `search` there is a single page instead: see below. |

`StoreListOptions` fields: `offset` (number of Actors to skip), `limit` (maximum number of Actors to
return; the API default and maximum is 1000, or 100 with `search`), `search` (search query),
`sortBy` (deprecated: the API ignores it, and orders the results by relevance with `search` and by
Store position otherwise, so sort them on the client), `category` (filter by one or more category IDs
such as `AI_AND_ML`, comma-separated and case-insensitive; an Actor must match all of them, and an
unknown category fails with a `400` error), `username` (filter Actors by owner username),
`pricingModel` (filter by pricing model: `FREE`, `PRICE_PER_DATASET_ITEM`, `PAY_PER_EVENT`; the
deprecated rental model `FLAT_PRICE_PER_MONTH` is no longer listed),
`includeUnrunnableActors` (include Actors the current user cannot run), `allowsAgenticUsers` (only
Actors that allow agentic users), `responseFormat` (the response shape: `full` or `agent`).

A `search` returns the Actors ranked by relevance as a single page of at most 100: the API ignores
`offset` and has no second page. `iterate` therefore sends one request for a search, when you call
`iterate` rather than on first demand, with the options' `limit` as its size. `chunkSize` and
`offset` have no effect. Narrow a search with `category`, `username`, or `pricingModel`.

```java
// Publishers.collect() drains the whole publisher; fine here since `limit` already caps it.
// To stop early without fetching further pages, subscribe with your own Flow.Subscriber instead
// (request(1) at a time, cancel() once you have enough); see IterateStore.java in the examples.
List<ActorStoreListItem> items =
    Publishers.collect(client.store().iterate(new StoreListOptions().username("apify").limit(20L), 10L))
        .join();
items.forEach(item -> System.out.println(item.getUsername() + "/" + item.getName()));

// A search is one page of at most 100 Actors.
List<ActorStoreListItem> found =
    Publishers.collect(client.store().iterate(new StoreListOptions().search("crawler"), null)).join();
```

`ActorStoreListItem` fields: `getId()`, `getName()`, `getUsername()`, `getTitle()`,
`getUserFullName()`, `getDescription()`, `getCategories()` (`List<String>` of category IDs),
`getNotice()`, `getStats()` (`StoreActorStats`: `getTotalUsers()`, `getTotalRuns()`,
`getTotalUsers7Days()`, `getTotalUsers30Days()`, `getTotalUsers90Days()`, `getActorReviewRating()`,
`getActorReviewCount()`), `getCurrentPricingInfo()` (`PricingInfo`: `getPricingModel()`),
`getIsWhiteListedForAgenticPayments()`, `getIsVerified()` (whether the developer has a verified
identity), `getBadge()`, `getPictureUrl()`, `getUserPictureUrl()`, `getUrl()`,
`getReadmeSummary()`. Any field not covered by a typed getter is
still available via the inherited `getExtra()` (see
[the docs index](README.md#model-fields-and-unmodeled-data-getextra)).

## Users: `client.me()` / `client.user(id)`

| Method | Description |
|---|---|
| `get()` | Fetch the user. Completes with `Optional<User>` (public profile, plus private account details for `me()` or when a full-permission token of that user, or one that impersonates it, is used). |
| `monthlyUsage()` / `monthlyUsage(String date)` | The user's monthly usage. `date` (any day within the target month, formatted `YYYY-MM-DD`) reports that month; `null`/empty reports the current month. Completes with `JsonNode`. |
| `limits()` | The user's account and usage limits. Completes with `JsonNode`. |
| `updateLimits(Object)` | Update the user's account and usage limits. Completes with no value (`CompletableFuture<Void>`). |

The usage/limits methods work on `me()` and on `user(id)` (`/v2/users/{userId}/usage/monthly` and
`/v2/users/{userId}/limits`), subject to what the API token may access.

```java
Optional<User> me = client.me().get().join();
me.ifPresent(
    u -> {
      System.out.println("Account: " + u.getId());
      // email/plan/proxy/... are private fields: see "Which fields are set" below.
      System.out.println("Email: " + u.getEmail());
    });
JsonNode usage = client.me().monthlyUsage().join();
```

Which fields are set:

- `getUsername()` is set, except for a proxy-password request (below), and `getProfile()` may be
  absent.
- The private fields (listed below, including `getId()`) are set for `me()`, and for `user(id)` only
  when the request uses a full-permission API token of that user or a token that impersonates it.
- `getPlan()`, `getEmail()`, and `getProfile()` are omitted when `me()` is requested from an Actor
  run.
- A request authenticated with an Apify Proxy password (`PROXY_PASSWORD` or
  `ACTOR_RUN_PROXY_PASSWORD`) gets reduced data on both endpoints: only `getPlan()` (with just
  `getAvailableProxyGroups()`), `getEffectivePlatformFeatures()`, and `getIsPaying()` are set, and
  `getId()` and `getUsername()` are `null`.

`User` fields: `getUsername()`, `getProfile()` (`UserProfile`: `getBio()`, `getReadme()`,
`getName()`, `getPictureUrl()`, `getGithubUsername()`, `getWebsiteUrl()`, `getTwitterUsername()`, all
nullable).
Private fields: `getId()`, `getEmail()`, `getProxy()` (`UserProxy`: `getPassword()`, `getGroups()`
as `List<ProxyGroup>`, each with `getName()`/`getDescription()`/`getAvailableCount()` as `Long`),
`getPlan()` (`UserPlan`: `getId()`, `getDescription()`, `getIsEnabled()`,
`getMonthlyBasePriceUsd()`/`getMonthlyUsageCreditsUsd()`/`getUsageDiscountPercent()`/
`getMaxMonthlyUsageUsd()`/`getMaxActorMemoryGbytes()`/`getMaxMonthlyActorComputeUnits()`/
`getMaxMonthlyResidentialProxyGbytes()`/`getMaxMonthlyProxySerps()`/
`getMaxMonthlyExternalDataTransferGbytes()` as `Double`, `getEnabledPlatformFeatures()` as
`List<String>`, `getMaxActorCount()`/`getMaxActorTaskCount()`/`getDataRetentionDays()`/
`getTeamAccountSeatCount()` as `Long`, `getAvailableProxyGroups()` as `JsonNode`,
`getSupportLevel()`, `getAvailableAddOns()` as `List<JsonNode>`), `getEffectivePlatformFeatures()`
(`JsonNode`), `getCreatedAt()` (`Instant`), `getIsPaying()` (`Boolean`). Plus `getExtra()`, a
`Map<String, Object>` carrying any account field not modeled directly.

## Logs: `client.log(id)`

Access a build's or run's log directly, or via `client.run(id).log()` / `client.build(id).log()`.

| Method | Description |
|---|---|
| `get()` / `get(LogOptions)` | The whole log as text. Completes with `Optional<String>` when addressed by an explicit id (`client.log(id)`); on `run.log()`/`build.log()` (no log id of its own), a 404 is ambiguous and throws `NotFoundError` instead; see [Fetching single resources](../README.md#fetching-single-resources). |
| `getAsBytes()` / `getAsBytes(LogOptions)` | As `get()`, but completes with `Optional<byte[]>` (the raw bytes, not decoded as text). Same 404 handling as `get()`. |
| `stream()` / `stream(LogOptions)` | A live `InputStream` over the log (for redirection). Completes with `InputStream`. The request is retried like any other, until the headers arrive; it throws on an error response that is not retried, or the last one once the retries run out, including a 404. |

`LogOptions` fields: `raw(Boolean)`, `download(Boolean)`.

```java
Optional<String> log = client.log("RUN_OR_BUILD_ID").get().join();
log.ifPresent(System.out::println);
```
