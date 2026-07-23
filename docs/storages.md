# Storages: datasets, key-value stores, request queues

The three storage types share a consistent shape: a collection client (`list`, `getOrCreate`) and a
single-resource client (`get`, `update`, `delete`, plus storage-specific operations). Run-nested
default storages are reachable via `client.run(id).dataset()` / `.keyValueStore()` /
`.requestQueue()`.

### Metadata models

The `get()`/`getOrCreate(...)` calls return the storage metadata model. All three share the common
getters `getId()`, `getName()`, `getUserId()`, `getCreatedAt()` (`Instant`), and `getModifiedAt()`
(`Instant`), plus a type-specific size counter:

| Model | Common getters | Type-specific |
|---|---|---|
| `Dataset` | `getId`, `getName`, `getUserId`, `getCreatedAt`, `getModifiedAt` | `getItemCount()` (`long`) |
| `KeyValueStore` | `getId`, `getName`, `getUserId`, `getCreatedAt`, `getModifiedAt` | — |
| `RequestQueue` | `getId`, `getName`, `getUserId`, `getCreatedAt`, `getModifiedAt` | `getTotalRequestCount()` (`long`) |

`Dataset`, `KeyValueStore`, `RequestQueue` and `RequestQueueRequest` all extend `ApifyResource`, so
any field not covered by a typed getter above is still available via the inherited `getExtra()`
(see [the docs index](README.md#model-fields-and-unmodeled-data-getextra)).

## Datasets

### `DatasetCollectionClient` — `client.datasets()`

| Method | Description |
|---|---|
| `list(StorageListOptions)` | List datasets. Completes with `PaginationList<Dataset>`. |
| `iterate(StorageListOptions, Long chunkSize)` | Lazy `Flow.Publisher<Dataset>` over all datasets; the options' `limit` caps the total yielded (`null`/unset or non-positive = all), `chunkSize` sets the per-request page size (`null` = server default). |
| `getOrCreate(String name)` | Get or create a named dataset (empty name → unnamed). Completes with `Dataset`. |
| `getOrCreate(String name, Object schema)` | As above, sending a creation-time dataset `schema` when a new dataset is created. Completes with `Dataset`. |

`StorageListOptions` adds `unnamed(Boolean)` and `ownership(String)` on top of offset/limit/desc.

### `DatasetClient` — `client.dataset(id)`

| Method | Description |
|---|---|
| `get()` / `update(Object)` / `delete()` | Metadata CRUD. |
| `listItems(DatasetListItemsOptions)` | List items. Completes with `PaginationList<JsonNode>`. |
| `listItems(DatasetListItemsOptions, Class<T>)` | List items decoded into `T`. Completes with `PaginationList<T>`. |
| `iterateItems(DatasetListItemsOptions)` / `iterateItems(DatasetListItemsOptions, Long chunkSize)` | A lazy `Flow.Publisher<JsonNode>` over all items; the options' `limit` caps the total yielded (`null`/unset or non-positive = all), the optional `chunkSize` sets the per-request page size (omitted/`null` = server default). |
| `iterateItems(DatasetListItemsOptions, Long chunkSize, Class<T>)` | As above, decoded into `T`. Returns `Flow.Publisher<T>`. For typed iteration at the server-default page size, pass a `null` chunk size: `iterateItems(opts, null, T.class)`. |
| `downloadItems(DownloadItemsFormat, DatasetDownloadOptions)` | Serialized bytes. `DownloadItemsFormat` is one of `JSON`, `JSONL`, `CSV`, `XLSX`, `XML`, `RSS`, `HTML`. Completes with `byte[]`. |
| `pushItems(Object)` | Push a single item or a list of items. Completes with no value (`CompletableFuture<Void>`). |
| `getStatistics()` | Dataset statistics. Completes with `Optional<JsonNode>`. |
| `createItemsPublicUrl(DatasetListItemsOptions, Long expiresInSecs)` | A public (optionally signed) items URL. Completes with `String`. |

> **Server-side item filters and iteration.** The dataset-items endpoint applies `offset`/`limit` to
> the raw items and then drops those removed by a server-side filter (`skipEmpty`, `skipHidden`,
> `clean`, `simplified`), so a page can contain fewer items than requested. Because `iterateItems`
> advances the offset by the number of items actually returned, combining it with those filters over a
> multi-page dataset has two failure modes: page windows can overlap and **repeat items**, and — more
> severely — if an entire offset window is filtered out the endpoint returns an empty page, which the
> iterator treats as the end, so iteration **stops early and silently skips the remaining data** (an
> all-filtered first page yields nothing at all). Prefer paging without server-side item filters when
> iterating, or fetch pages explicitly with `listItems` and filter client-side.

```java
Dataset ds = client.datasets().getOrCreate("my-dataset").join();
client.dataset(ds.getId()).pushItems(List.of(Map.of("url", "https://a.com"))).join();
PaginationList<JsonNode> page = client.dataset(ds.getId()).listItems(new DatasetListItemsOptions().limit(100L)).join();
byte[] csv = client.dataset(ds.getId()).downloadItems(DownloadItemsFormat.CSV, new DatasetDownloadOptions().bom(true)).join();
```

`DatasetListItemsOptions` fields: `offset` (`Long`, number of items to skip), `limit` (`Long`,
maximum number of items to return), `desc` (`Boolean`, return items newest-first), `fields`
(`List<String>`, restrict the output to these source fields), `outputFields` (`List<String>`,
positionally *renames* the fields chosen by `fields` in the output — the i-th name renames the i-th
`fields` entry, so it only makes sense together with `fields`), `omit` (`List<String>`, exclude
these fields from the output), `skipEmpty` (`Boolean`, skip empty items), `skipHidden` (`Boolean`,
skip hidden fields, i.e. those starting with `#`), `clean` (`Boolean`, return only clean —
non-empty, non-hidden — items), `unwind` (`List<String>`, expand these fields so each array element
becomes a separate item), `flatten` (`List<String>`, flatten these nested fields into dot-notation
keys), `view` (`String`, select a predefined dataset view for field selection), `simplified`
(`Boolean`, return simplified — flattened and cleaned — items), `skipFailedPages` (`Boolean`, skip
items that come from failed pages), `signature` (`String`, a pre-shared URL signature granting
access without an API token). `downloadItems(...)` returns `byte[]` (the serialized export).
`DatasetDownloadOptions` wraps a `DatasetListItemsOptions` (`items(DatasetListItemsOptions)`) and
adds `attachment` (`Boolean`), `bom` (`Boolean`), `delimiter` (`String`), `skipHeaderRow`
(`Boolean`), `xmlRoot` (`String`), `xmlRow` (`String`), `feedTitle` (`String`), `feedDescription`
(`String`).

`createItemsPublicUrl(DatasetListItemsOptions, Long expiresInSecs)` completes with a `String` URL.
If the dataset is private, the client fetches it, reads its URL-signing secret, and appends an
HMAC-SHA256 signature (bounded by `expiresInSecs`, or non-expiring when `null`); for public datasets
the URL is unsigned.

> **Public-URL limitation (matches the JavaScript reference client).** The public-URL builders
> (`createItemsPublicUrl`, and the key-value-store `getRecordPublicUrl` / `createKeysPublicUrl`)
> build the URL from the client's own resource path plus the signature. When called on a *last-run
> nested* client (e.g. `client.actor(id).lastRun("SUCCEEDED").dataset().createItemsPublicUrl(...)`)
> the returned URL keeps the unpinned `.../runs/last/dataset` path and does not carry the `status`
> filter, so it may resolve a different run when opened. Build public URLs from a concrete
> `client.dataset(id)` / `client.keyValueStore(id)` client when you need a stable shareable URL.

## Key-value stores

### `KeyValueStoreCollectionClient` — `client.keyValueStores()`

`list(StorageListOptions)`, `iterate(StorageListOptions, Long chunkSize)`, `getOrCreate(String)`, and
`getOrCreate(String, Object schema)` (the latter sends a creation-time store `schema`), as for
datasets.

### `KeyValueStoreClient` — `client.keyValueStore(id)`

| Method | Description |
|---|---|
| `get()` / `update(Object)` / `delete()` | Metadata CRUD. |
| `listKeys(ListKeysOptions)` | List keys. Completes with `KeyValueStoreKeysPage`. |
| `iterateKeys(ListKeysOptions)` / `iterateKeys(ListKeysOptions, Long chunkSize)` | A lazy `Flow.Publisher<KeyValueStoreKey>` over all keys, paging with the cursor (`exclusiveStartKey`). Note: here the options' `limit` caps the **total** number of keys yielded (`null`/unset or non-positive = all), whereas for `listKeys`/`createKeysPublicUrl` the same `ListKeysOptions.limit` is a single-request page size. `chunkSize` sets the per-request page size (`null` = server default). |
| `recordExists(String key)` | Whether a record exists. Completes with `boolean`. |
| `getRecord(String key)` / `getRecord(String key, GetRecordOptions)` | Fetch a record. Completes with `Optional<KeyValueStoreRecord>`. |
| `setRecord(String key, byte[] value, String contentType)` | Store raw bytes. Completes with no value (`CompletableFuture<Void>`). |
| `setRecord(String key, byte[] value, String contentType, SetRecordOptions)` | Store raw bytes with write options (`SetRecordOptions`: `timeoutSecs` (`Long`), `doNotRetryTimeouts` (`Boolean`)). Completes with no value. |
| `setRecordJson(String key, Object value)` | Store JSON. Completes with no value. |
| `deleteRecord(String key)` | Delete a record. Completes with no value. |
| `getRecordPublicUrl(String key)` | A public (optionally signed) record URL. Completes with `String`. |
| `createKeysPublicUrl(Long expiresInSecs)` | A public (optionally signed) key-list URL. Completes with `String`. |
| `createKeysPublicUrl(ListKeysOptions, Long expiresInSecs)` | As above, forwarding key-listing filters (`limit`, `prefix`, `collection`, `exclusiveStartKey`). |

`ListKeysOptions` fields (all optional): `limit(Long)`, `exclusiveStartKey(String)`,
`prefix(String)` (restrict to keys with this prefix), `collection(String)` (a named collection of
keys), `signature(String)` (a pre-shared URL signature granting access without an API token, used
by `createKeysPublicUrl`).

```java
KeyValueStore store = client.keyValueStores().getOrCreate("my-store").join();
client.keyValueStore(store.getId()).setRecordJson("OUTPUT", Map.of("answer", 42)).join();
Optional<KeyValueStoreRecord> rec = client.keyValueStore(store.getId()).getRecord("OUTPUT").join();
rec.ifPresent(r -> System.out.println(new String(r.getValue())));
```

`GetRecordOptions` (for `getRecord(String key, GetRecordOptions)`) fields: `attachment(Boolean)`
(request a download disposition) and `signature(String)` (a pre-computed access signature).

`KeyValueStoreRecord` exposes `getKey()`, `getValue()` (raw bytes) and `getContentType()`.
`KeyValueStoreKeysPage` exposes `getItems()` (a list of `KeyValueStoreKey` with `getKey()`/`getSize()`),
`isTruncated()`, `getExclusiveStartKey()` and `getNextExclusiveStartKey()`.

Both `getRecordPublicUrl` and `createKeysPublicUrl` complete with a `String` URL, signed for private stores
and unsigned for public ones. They differ because they sign different things: a single record URL
signs the record key directly (there is no expiry to bound), while a key-list URL uses an
expiry-aware storage-content signature — hence only `createKeysPublicUrl` takes an `expiresInSecs`
(`null` = non-expiring).

## Request queues

### `RequestQueueCollectionClient` — `client.requestQueues()`

`list(StorageListOptions)`, `iterate(StorageListOptions, Long chunkSize)`, and `getOrCreate(String)`,
as for datasets. Unlike datasets/key-value stores, there is no `getOrCreate(String, Object schema)`
overload here — the request-queue creation endpoint does not accept a creation-time schema.

### `RequestQueueClient` — `client.requestQueue(id)`

| Method | Description |
|---|---|
| `get()` / `update(Object)` / `delete()` | Metadata CRUD. |
| `withClientKey(String)` | A copy that identifies its requests with a stable client key (required for lock operations). |
| `listHead(Long limit)` | Requests at the head. Completes with `RequestQueueHead`. |
| `addRequest(RequestQueueRequest, boolean forefront)` | Add a request. Completes with `RequestQueueOperationInfo`. |
| `getRequest(String id)` | Fetch a request. Completes with `Optional<RequestQueueRequest>`. |
| `updateRequest(RequestQueueRequest, boolean forefront)` | Update a request. Completes with `RequestQueueOperationInfo`. |
| `deleteRequest(String id)` | Delete a request. Completes with no value (`CompletableFuture<Void>`). |
| `batchAddRequests(List<RequestQueueRequest>, boolean forefront)` | Add many (auto-chunked at 25 requests *and* by the API's ~9 MiB payload-size limit; unprocessed requests retried). Completes with `BatchAddResult`. |
| `batchAddRequests(List<RequestQueueRequest>, boolean forefront, BatchAddRequestsOptions)` | As above, tuning `maxUnprocessedRequestsRetries`, `maxParallel` (bounds how many chunk requests are in flight concurrently) and `minDelayBetweenUnprocessedRequestsRetriesMillis`. |
| `batchDeleteRequests(Object)` | Delete many. Completes with `BatchDeleteResult`. |
| `listAndLockHead(long lockSecs, Long limit)` | Atomically lock the head. Completes with `LockedRequestQueueHead`. |
| `listRequests(ListRequestsOptions)` | List requests. Completes with `RequestsList`. |
| `prolongRequestLock(String id, long lockSecs, boolean forefront)` | Extend a lock. Completes with `RequestLockInfo`. |
| `deleteRequestLock(String id, boolean forefront)` | Release a lock. Completes with no value. |
| `unlockRequests()` | Release all the client's locks. Completes with `UnlockRequestsResult`. |
| `paginateRequests(Long pageLimit)` | A lazy `Flow.Publisher<RequestQueueRequest>` over all requests, paging with the queue's forward cursor. Equivalent to `paginateRequests(null, pageLimit, null)`. |
| `paginateRequests(Long totalLimit, Long chunkSize, List<String> filter)` | As above, with a cap on the total number yielded (`null`/non-positive = unbounded) and an optional state `filter` (`ListRequestsOptions.FILTER_LOCKED`/`FILTER_PENDING`), matching `listRequests`'s filter. |

> **The `forefront` parameter.** `addRequest`, `updateRequest`, `batchAddRequests`,
> `prolongRequestLock` and `deleteRequestLock` all take a `boolean forefront`. It controls queue
> priority: `false` (the common case) processes the request in normal FIFO order; `true` inserts
> or moves the request to the *front* of the queue, so it is returned before anything already
> queued — use it for urgent/priority work that should jump the line, e.g. re-queuing a failed
> request for immediate retry or seeding a crawl's very first URLs.

```java
// Retry a failed request ahead of everything else already queued.
RequestQueueClient queue = client.requestQueue("QUEUE_ID");
RequestQueueRequest failed = queue.getRequest("REQUEST_ID").join().orElseThrow();
queue.updateRequest(failed.setNoRetry(false), true).join();
```

> **Naming exception.** Request-queue *requests* are iterated with `paginateRequests(...)` — not an
> `iterate(...)` method — because the request-queue listing is cursor-based rather than
> offset/limit; it always starts from the beginning of the queue (resuming from an explicit
> `exclusiveStartId`/`cursor` is not supported by the publisher — use `listRequests(ListRequestsOptions)`
> directly for that single-page use case). Every other resource uses the
> `iterate`/`iterateItems`/`iterateKeys` family.

```java
RequestQueue rq = client.requestQueues().getOrCreate("my-queue").join();
RequestQueueClient queue = client.requestQueue(rq.getId());
queue.addRequest(new RequestQueueRequest("https://example.com", "example"), false).join();
List<RequestQueueRequest> requests = Publishers.collect(queue.paginateRequests(100L)).join();
requests.forEach(r -> System.out.println(r.getUrl()));
```

> **Lock lifecycle for distributed crawling.** `withClientKey(String)` returns a copy of the client
> that stamps every request with a stable identifier, so the queue can tell which client holds which
> lock; use the *same* `RequestQueueClient` instance (or another built with the same client key) for
> the whole lock/unlock cycle below. `listAndLockHead` atomically reserves requests so other workers
> using a different client key cannot receive them until the lock expires or is explicitly released.

```java
RequestQueueClient queue = client.requestQueue("QUEUE_ID").withClientKey("worker-1");

// Atomically reserve up to 10 requests from the head, locked for 60s.
LockedRequestQueueHead locked = queue.listAndLockHead(60L, 10L).join();
for (RequestQueueRequest request : locked.getItems()) {
  boolean handledSuccessfully = true; // replace with your own processing logic/result
  if (handledSuccessfully) {
    queue.deleteRequest(request.getId()).join();
  } else {
    // Give up on this one: release its lock so another worker can pick it up instead.
    queue.deleteRequestLock(request.getId(), false).join();
  }
}

// Still working on a request past its lock's expiry? Extend it instead of losing it mid-process.
queue.prolongRequestLock(locked.getItems().get(0).getId(), 60L, false).join();

// On shutdown, release every lock this client still holds so other workers are not blocked
// waiting for locks this worker will never finish processing.
queue.unlockRequests().join();
```

`ListRequestsOptions` fields: `limit`, `exclusiveStartId`, `cursor` (mutually exclusive with
`exclusiveStartId`), and `filter(List<String>)` restricted to `ListRequestsOptions.FILTER_LOCKED` /
`FILTER_PENDING`.

`RequestQueueRequest` models a request. Its `(url, uniqueKey)` constructor covers the common case
(`uniqueKey` is the deduplication key); fluent setters and matching getters cover the rest (unset
fields are omitted on the wire): `setId`/`getId` (`String`), `setUrl`/`getUrl` (`String`),
`setUniqueKey`/`getUniqueKey` (`String`), `setMethod`/`getMethod` (`String`),
`setUserData(JsonNode)`/`getUserData()` (`JsonNode`),
`setPayload(String)`/`getPayload()` (`String`, the HTTP request body),
`setHeaders(Map<String, String>)`/`getHeaders()` (`Map<String, String>`),
`setNoRetry(Boolean)`/`getNoRetry()` (`Boolean`),
`setHandledAt(Instant)`/`getHandledAt()` (`Instant`),
`setRetryCount(Integer)`/`getRetryCount()` (`Integer`),
`setLoadedUrl(String)`/`getLoadedUrl()` (`String`, the URL actually loaded, after redirects), and
`setErrorMessages(List<String>)`/`getErrorMessages()` (`List<String>`, never `null` — empty when
unset). `getLockExpiresAt()` (read-only, no setter, `Instant`) is populated only on items returned
from `listAndLockHead`.

Return types:
- `RequestQueueOperationInfo` (from `addRequest`/`updateRequest`): `getRequestId()`,
  `isWasAlreadyPresent()`, `isWasAlreadyHandled()`.
- `RequestQueueHead` (from `listHead`): `getItems()` (a list of `RequestQueueRequest`),
  `getLimit()`, `getQueueModifiedAt()` (`Instant`), `isHadMultipleClients()`.
- `BatchAddResult` (from `batchAddRequests`): `getProcessedRequests()` (a list of
  `RequestQueueOperationInfo`) and `getUnprocessedRequests()` (a list of `RequestQueueRequest`).
- `BatchDeleteResult` (from `batchDeleteRequests`): `getProcessedRequests()` (a list of
  `DeletedRequestInfo`, exposing `getId()`/`getUniqueKey()`) and `getUnprocessedRequests()` (a list
  of `RequestQueueRequest`).
- `LockedRequestQueueHead` (from `listAndLockHead`): as `RequestQueueHead` plus `getLockSecs()`,
  `isQueueHasLockedRequests()` and `getClientKey()`; each item's `getLockExpiresAt()` is populated.
- `RequestsList` (from `listRequests`): `getItems()`, `getLimit()`, `getCursor()`,
  `getNextCursor()` (pass to a further `listRequests` call to continue paging).
- `RequestLockInfo` (from `prolongRequestLock`): `getLockExpiresAt()` (`Instant`).
- `UnlockRequestsResult` (from `unlockRequests`): `getUnlockedCount()`.

`batchDeleteRequests(Object)` accepts a JSON-serializable list of request identifiers (each with an
`id` or `uniqueKey`).
