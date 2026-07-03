# Storages: datasets, key-value stores, request queues

The three storage types share a consistent shape: a collection client (`list`, `getOrCreate`) and a
single-resource client (`get`, `update`, `delete`, plus storage-specific operations). Run-nested
default storages are reachable via `client.run(id).dataset()` / `.keyValueStore()` /
`.requestQueue()`.

## Datasets

### `DatasetCollectionClient` — `client.datasets()`

| Method | Description |
|---|---|
| `list(StorageListOptions)` | List datasets. Returns `PaginationList<Dataset>`. |
| `getOrCreate(String name)` | Get or create a named dataset (empty name → unnamed). Returns `Dataset`. |

`StorageListOptions` adds `unnamed(Boolean)` and `ownership(String)` on top of offset/limit/desc.

### `DatasetClient` — `client.dataset(id)`

| Method | Description |
|---|---|
| `get()` / `update(Object)` / `delete()` | Metadata CRUD. |
| `listItems(DatasetListItemsOptions)` | List items as `PaginationList<JsonNode>`. |
| `listItems(DatasetListItemsOptions, Class<T>)` | List items decoded into `T`. |
| `downloadItems(DownloadItemsFormat, DatasetDownloadOptions)` | Serialized bytes (JSON/JSONL/CSV/XLSX/XML/RSS/HTML). |
| `pushItems(Object)` | Push a single item or a list of items. |
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

## Key-value stores

### `KeyValueStoreCollectionClient` — `client.keyValueStores()`

`list(StorageListOptions)` and `getOrCreate(String)`, as for datasets.

### `KeyValueStoreClient` — `client.keyValueStore(id)`

| Method | Description |
|---|---|
| `get()` / `update(Object)` / `delete()` | Metadata CRUD. |
| `listKeys(ListKeysOptions)` | List keys. Returns `KeyValueStoreKeysPage`. |
| `recordExists(String key)` | Whether a record exists. |
| `getRecord(String key)` / `getRecord(String key, GetRecordOptions)` | Fetch a record. Returns `Optional<KeyValueStoreRecord>`. |
| `setRecord(String key, byte[] value, String contentType)` | Store raw bytes. |
| `setRecord(String key, byte[] value, String contentType, SetRecordOptions)` | Store raw bytes with write options (`timeoutSecs`, `doNotRetryTimeouts`). |
| `setRecordJson(String key, Object value)` | Store JSON. |
| `deleteRecord(String key)` | Delete a record. |
| `getRecordPublicUrl(String key)` | A public (optionally signed) record URL. |
| `createKeysPublicUrl(Long expiresInSecs)` | A public (optionally signed) key-list URL. |
| `createKeysPublicUrl(ListKeysOptions, Long expiresInSecs)` | As above, forwarding key-listing filters (`limit`, `prefix`, `collection`, `exclusiveStartKey`). |

```java
KeyValueStore store = client.keyValueStores().getOrCreate("my-store");
client.keyValueStore(store.getId()).setRecordJson("OUTPUT", Map.of("answer", 42));
Optional<KeyValueStoreRecord> rec = client.keyValueStore(store.getId()).getRecord("OUTPUT");
rec.ifPresent(r -> System.out.println(new String(r.getValue())));
```

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

`list(StorageListOptions)` and `getOrCreate(String)`, as for datasets.

### `RequestQueueClient` — `client.requestQueue(id)`

| Method | Description |
|---|---|
| `get()` / `update(Object)` / `delete()` | Metadata CRUD. |
| `withClientKey(String)` | A copy that identifies its requests with a stable client key (required for lock operations). |
| `listHead(Long limit)` | Requests at the head. Returns `RequestQueueHead`. |
| `addRequest(RequestQueueRequest, boolean forefront)` | Add a request. Returns `RequestQueueOperationInfo`. |
| `getRequest(String id)` | Fetch a request. Returns `Optional<RequestQueueRequest>`. |
| `updateRequest(RequestQueueRequest, boolean forefront)` | Update a request. |
| `deleteRequest(String id)` | Delete a request. |
| `batchAddRequests(List<RequestQueueRequest>, boolean forefront)` | Add many (auto-chunked at 25, unprocessed requests retried). Returns `BatchAddResult`. |
| `batchAddRequests(List<RequestQueueRequest>, boolean forefront, BatchAddRequestsOptions)` | As above, tuning `maxUnprocessedRequestsRetries`, `maxParallel` and `minDelayBetweenUnprocessedRequestsRetriesMillis`. |
| `batchDeleteRequests(Object)` | Delete many. Returns `JsonNode`. |
| `listAndLockHead(long lockSecs, Long limit)` | Atomically lock the head. Returns `JsonNode`. |
| `listRequests(ListRequestsOptions)` | List requests. Returns `JsonNode`. |
| `prolongRequestLock(String id, long lockSecs, boolean forefront)` | Extend a lock. Returns `JsonNode`. |
| `deleteRequestLock(String id, boolean forefront)` | Release a lock. |
| `unlockRequests()` | Release all the client's locks. Returns `JsonNode`. |
| `paginateRequests(Long pageLimit)` | A lazy `Iterator<RequestQueueRequest>` over all requests. |

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
