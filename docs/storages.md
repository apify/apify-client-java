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

## Datasets

### `DatasetCollectionClient` — `client.datasets()`

| Method | Description |
|---|---|
| `list(StorageListOptions)` | List datasets. Returns `PaginationList<Dataset>`. |
| `iterate(StorageListOptions, Long chunkSize)` | Lazy `Iterator<Dataset>` over all datasets; the options' `limit` caps the total yielded (`null` = all), `chunkSize` sets the per-request page size (`null` = server default). |
| `getOrCreate(String name)` | Get or create a named dataset (empty name → unnamed). Returns `Dataset`. |
| `getOrCreate(String name, Object schema)` | As above, sending a creation-time dataset `schema` when a new dataset is created. Returns `Dataset`. |

`StorageListOptions` adds `unnamed(Boolean)` and `ownership(String)` on top of offset/limit/desc.

### `DatasetClient` — `client.dataset(id)`

| Method | Description |
|---|---|
| `get()` / `update(Object)` / `delete()` | Metadata CRUD. |
| `listItems(DatasetListItemsOptions)` | List items as `PaginationList<JsonNode>`. |
| `listItems(DatasetListItemsOptions, Class<T>)` | List items decoded into `T`. Returns `PaginationList<T>`. |
| `iterateItems(DatasetListItemsOptions, Long chunkSize)` | Lazy `Iterator<JsonNode>` over all items; the options' `limit` caps the total yielded (`null` = all), `chunkSize` sets the per-request page size (`null` = server default). |
| `iterateItems(DatasetListItemsOptions, Long chunkSize, Class<T>)` | As above, decoded into `T`. Returns `Iterator<T>`. |

> **Server-side item filters and iteration.** The dataset-items endpoint applies `offset`/`limit` to
> the raw items and then drops those removed by a server-side filter (`skipEmpty`, `skipHidden`,
> `clean`, `simplified`), so a page can contain fewer items than requested. Because `iterateItems`
> advances the offset by the number of items actually returned, combining it with those filters over a
> multi-page dataset can make page windows overlap and repeat items. Prefer paging without server-side
> item filters when iterating, or de-duplicate on the client.
| `downloadItems(DownloadItemsFormat, DatasetDownloadOptions)` | Serialized bytes (JSON/JSONL/CSV/XLSX/XML/RSS/HTML). |
| `pushItems(Object)` | Push a single item or a list of items. No return value. |
| `getStatistics()` | Dataset statistics. Returns `Optional<JsonNode>`. |
| `createItemsPublicUrl(DatasetListItemsOptions, Long expiresInSecs)` | A public (optionally signed) items URL. |

```java
Dataset ds = client.datasets().getOrCreate("my-dataset");
client.dataset(ds.getId()).pushItems(List.of(Map.of("url", "https://a.com")));
PaginationList<JsonNode> page = client.dataset(ds.getId()).listItems(new DatasetListItemsOptions().limit(100L));
byte[] csv = client.dataset(ds.getId()).downloadItems(DownloadItemsFormat.CSV, new DatasetDownloadOptions().bom(true));
```

`DatasetListItemsOptions` fields: `offset`, `limit`, `desc`, `fields`, `outputFields`, `omit`,
`skipEmpty`, `skipHidden`, `clean`, `unwind`, `flatten`, `view`, `simplified`, `skipFailedPages`,
`signature`. `fields` selects which source fields to include; `outputFields` positionally *renames*
the fields chosen by `fields` in the output (the i-th name renames the i-th `fields` entry), so it
only makes sense together with `fields`. `downloadItems(...)` returns `byte[]` (the serialized
export). `DatasetDownloadOptions` wraps a `DatasetListItemsOptions` (`items(...)`) and adds
`attachment`, `bom`, `delimiter`, `skipHeaderRow`, `xmlRoot`, `xmlRow`, `feedTitle`,
`feedDescription`.

`createItemsPublicUrl(DatasetListItemsOptions, Long expiresInSecs)` returns a `String` URL. If the
dataset is private, the client fetches it, reads its URL-signing secret, and appends an HMAC-SHA256
signature (bounded by `expiresInSecs`, or non-expiring when `null`); for public datasets the URL is
unsigned.

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
| `listKeys(ListKeysOptions)` | List keys. Returns `KeyValueStoreKeysPage`. |
| `iterateKeys(ListKeysOptions)` | Lazy `Iterator<KeyValueStoreKey>` over all keys, paging with the cursor (`exclusiveStartKey`). Note: here the options' `limit` caps the **total** number of keys yielded (`null` = all), whereas for `listKeys`/`createKeysPublicUrl` the same `ListKeysOptions.limit` is a single-request page size. Key iteration is cursor-based, so it has no separate `chunkSize`. |
| `recordExists(String key)` | Whether a record exists. |
| `getRecord(String key)` / `getRecord(String key, GetRecordOptions)` | Fetch a record. Returns `Optional<KeyValueStoreRecord>`. |
| `setRecord(String key, byte[] value, String contentType)` | Store raw bytes. No return value. |
| `setRecord(String key, byte[] value, String contentType, SetRecordOptions)` | Store raw bytes with write options (`timeoutSecs`, `doNotRetryTimeouts`). No return value. |
| `setRecordJson(String key, Object value)` | Store JSON. No return value. |
| `deleteRecord(String key)` | Delete a record. No return value. |
| `getRecordPublicUrl(String key)` | A public (optionally signed) record URL. |
| `createKeysPublicUrl(Long expiresInSecs)` | A public (optionally signed) key-list URL. |
| `createKeysPublicUrl(ListKeysOptions, Long expiresInSecs)` | As above, forwarding key-listing filters (`limit`, `prefix`, `collection`, `exclusiveStartKey`). |

```java
KeyValueStore store = client.keyValueStores().getOrCreate("my-store");
client.keyValueStore(store.getId()).setRecordJson("OUTPUT", Map.of("answer", 42));
Optional<KeyValueStoreRecord> rec = client.keyValueStore(store.getId()).getRecord("OUTPUT");
rec.ifPresent(r -> System.out.println(new String(r.getValue())));
```

`GetRecordOptions` (for `getRecord(String key, GetRecordOptions)`) fields: `attachment(Boolean)`
(request a download disposition) and `signature(String)` (a pre-computed access signature).

`KeyValueStoreRecord` exposes `getKey()`, `getValue()` (raw bytes) and `getContentType()`.
`KeyValueStoreKeysPage` exposes `getItems()` (a list of `KeyValueStoreKey` with `getKey()`/`getSize()`),
`isTruncated()`, `getExclusiveStartKey()` and `getNextExclusiveStartKey()`.

Both `getRecordPublicUrl` and `createKeysPublicUrl` return a `String` URL, signed for private stores
and unsigned for public ones. They differ because they sign different things: a single record URL
signs the record key directly (there is no expiry to bound), while a key-list URL uses an
expiry-aware storage-content signature — hence only `createKeysPublicUrl` takes an `expiresInSecs`
(`null` = non-expiring).

## Request queues

### `RequestQueueCollectionClient` — `client.requestQueues()`

`list(StorageListOptions)`, `iterate(StorageListOptions, Long chunkSize)`, and `getOrCreate(String)`,
as for datasets.

### `RequestQueueClient` — `client.requestQueue(id)`

| Method | Description |
|---|---|
| `get()` / `update(Object)` / `delete()` | Metadata CRUD. |
| `withClientKey(String)` | A copy that identifies its requests with a stable client key (required for lock operations). |
| `listHead(Long limit)` | Requests at the head. Returns `RequestQueueHead`. |
| `addRequest(RequestQueueRequest, boolean forefront)` | Add a request. Returns `RequestQueueOperationInfo`. |
| `getRequest(String id)` | Fetch a request. Returns `Optional<RequestQueueRequest>`. |
| `updateRequest(RequestQueueRequest, boolean forefront)` | Update a request. Returns `RequestQueueOperationInfo`. |
| `deleteRequest(String id)` | Delete a request. No return value. |
| `batchAddRequests(List<RequestQueueRequest>, boolean forefront)` | Add many (auto-chunked at 25, unprocessed requests retried). Returns `BatchAddResult`. |
| `batchAddRequests(List<RequestQueueRequest>, boolean forefront, BatchAddRequestsOptions)` | As above, tuning `maxUnprocessedRequestsRetries`, `maxParallel` and `minDelayBetweenUnprocessedRequestsRetriesMillis`. |
| `batchDeleteRequests(Object)` | Delete many. Returns `JsonNode`. |
| `listAndLockHead(long lockSecs, Long limit)` | Atomically lock the head. Returns `JsonNode`. |
| `listRequests(ListRequestsOptions)` | List requests. Returns `JsonNode`. |
| `prolongRequestLock(String id, long lockSecs, boolean forefront)` | Extend a lock. Returns `JsonNode`. |
| `deleteRequestLock(String id, boolean forefront)` | Release a lock. No return value. |
| `unlockRequests()` | Release all the client's locks. Returns `JsonNode`. |
| `paginateRequests(Long pageLimit)` | A lazy `Iterator<RequestQueueRequest>` over all requests, paging with the queue's forward cursor. |

> **Naming exception.** Request-queue *requests* are iterated with `paginateRequests(Long pageLimit)`
> — not an `iterate(...)` method — because the request-queue listing is cursor-based rather than
> offset/limit. Its single argument is the per-request page size; there is no total-items cap. Every
> other resource uses the `iterate`/`iterateItems`/`iterateKeys` family.

```java
RequestQueue rq = client.requestQueues().getOrCreate("my-queue");
RequestQueueClient queue = client.requestQueue(rq.getId());
queue.addRequest(new RequestQueueRequest("https://example.com", "example"), false);
Iterator<RequestQueueRequest> it = queue.paginateRequests(100L);
while (it.hasNext()) {
  System.out.println(it.next().getUrl());
}
```

`ListRequestsOptions` fields: `limit`, `exclusiveStartId`, `cursor` (mutually exclusive with
`exclusiveStartId`), and `filter(List<String>)` restricted to `ListRequestsOptions.FILTER_LOCKED` /
`FILTER_PENDING`.

`RequestQueueRequest` models a request. Its `(url, uniqueKey)` constructor covers the common case
(`uniqueKey` is the deduplication key); fluent setters `setId`, `setUrl`, `setUniqueKey`,
`setMethod`, `setUserData(JsonNode)` and matching getters cover the rest (unset fields are omitted on
the wire).

Return types:
- `RequestQueueOperationInfo` (from `addRequest`/`updateRequest`): `getRequestId()`,
  `isWasAlreadyPresent()`, `isWasAlreadyHandled()`.
- `RequestQueueHead` (from `listHead`): `getItems()` (a list of `RequestQueueRequest`),
  `getLimit()`, `isHadMultipleClients()`.
- `BatchAddResult` (from `batchAddRequests`): `getProcessedRequests()` (a list of
  `RequestQueueOperationInfo`) and `getUnprocessedRequests()` (a list of `RequestQueueRequest`).

The remaining lock/list operations return raw `JsonNode` (see
[Raw JSON values](README.md#raw-json-values)); `batchDeleteRequests(Object)` accepts a
JSON-serializable list of request identifiers (each with an `id` or `uniqueKey`).
