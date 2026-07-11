# Changelog

All notable changes to the Apify Java client are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-07-10

### Added

- Lazy iteration helpers over every paginated collection, matching the reference JS client's
  iterable `list()`: `iterate(options, chunkSize)` on the Actor, build, run, dataset,
  key-value-store, request-queue, task, schedule, webhook, and webhook-dispatch collection clients;
  and `DatasetClient.iterateItems(...)` for dataset items and `KeyValueStoreClient.iterateKeys(...)`
  for store keys. The options' `limit` caps the total number of items yielded and `chunkSize` sets
  the per-request page size. The non-paginated collections — `ActorVersionCollectionClient.iterate(options)`
  and `ActorEnvVarCollectionClient.iterate()` — return the full list in a single fetch (no page size
  to tune).

### Changed

- Verified the client against OpenAPI specification version `v2-2026-07-10T105921Z` and bumped
  `Version.API_SPEC_VERSION` accordingly. The spec delta is forward-compatible: new `401`/`402`
  error responses on several endpoints (handled generically by `ApifyApiException`) and relaxed
  nullability/optionality on some response fields — already tolerated because the models use
  nullable boxed field types (a JSON `null` deserializes to `null`) and an optional field simply
  stays unset.

### Breaking

- `StoreCollectionClient.iterate` now takes `iterate(StoreListOptions, Long chunkSize)`, where the
  options' `limit` is the total-items cap and `chunkSize` is the page size. Previously `limit` was
  the per-page size. This aligns Store iteration with the reference client and the new collection
  iterators.

## [0.1.3] - 2026-07-10

### Added

- Request-body compression now prefers brotli (`Content-Encoding: br`), matching the reference JS
  client, via the `brotli4j` native codec. Gzip (`Content-Encoding: gzip`) remains the fallback when
  no brotli native binary is available for the running platform.

### Changed

- Extended the `User-Agent` OS-token mapping test to assert the emitted token exactly matches the
  reference JS client's `os.platform()` value for every platform the JVM can run on (`sunos`,
  `freebsd`, `openbsd`, `aix`, in addition to `linux`, `darwin`, `win32`, `android`).

## [0.1.2] - 2026-07-09

### Changed

- Verified the client against OpenAPI specification version `v2-2026-07-08T143931Z` and bumped
  `Version.API_SPEC_VERSION` accordingly.
- Aligned the `User-Agent` OS token with the reference JS client's `os.platform()` token: it now
  uses the short, lowercase platform identifier (`linux`, `darwin`, `win32`, `android`, …) instead
  of the human-readable `os.name` value.

### Added

- Request bodies of 1024 bytes or more are now gzip-compressed and sent with
  `Content-Encoding: gzip`.

## [0.1.1] - 2026-07-07

### Changed

- Verified the client against OpenAPI specification version `v2-2026-07-07T132551Z` and bumped
  `Version.API_SPEC_VERSION` accordingly.
- Corrected the `LastRunOptions` documentation: `origin` is now a spec-declared query parameter on
  the `runs/last` endpoints (behaviour unchanged).

## [0.1.0] - 2026-07-03

Initial release of the official (experimental, AI-generated and AI-maintained) Java client for the
Apify API, verified against OpenAPI specification version `v2-2026-07-02T131926Z`.

### Added

- Resource-oriented `ApifyClient` mirroring the JavaScript reference client, with accessors for
  Actors, Actor versions and environment variables, builds, runs,
  datasets, key-value stores, request queues, tasks, schedules, webhooks, webhook dispatches, the
  Apify Store, users, and logs.
- Replaceable HTTP transport via the `HttpBackend` interface (default `DefaultHttpBackend`, backed
  by the JDK `java.net.http.HttpClient`), configurable through `ApifyClient.builder().httpBackend(...)`.
- Cross-cutting request behaviour applied to every call: bearer-token authentication, the mandated
  `User-Agent` header, exponential-backoff-with-jitter retries (429, 5xx and network errors), and a
  growing-but-capped per-attempt timeout.
- Convenience helpers matching the reference client: `actor(...).call` / `task(...).call` (start and
  wait), `run(...).waitForFinish` / `build(...).waitForFinish`, `actor(...).defaultBuild`,
  `run(...).metamorph`/`reboot`/`resurrect`/`charge`, run-nested default storages, lazy
  `store().iterate()` and `requestQueue(...).paginateRequests()` iterators, dataset
  `downloadItems`/`getStatistics`/`createItemsPublicUrl`, key-value-store record and key-list public
  URLs with HMAC-SHA256 signing, the request-queue lock lifecycle, and `setStatusMessage`.
- `setRecord` write options (`SetRecordOptions`: `timeoutSecs`, `doNotRetryTimeouts`),
  `createKeysPublicUrl(ListKeysOptions, ...)` key-listing filters, and `batchAddRequests`
  tuning (`BatchAddRequestsOptions`: `maxUnprocessedRequestsRetries`, `maxParallel`,
  `minDelayBetweenUnprocessedRequestsRetriesMillis`) with automatic retry of unprocessed requests,
  all matching the reference client. `downloadItems` forwards the full set of item-selection
  parameters via `DatasetDownloadOptions.items(...)`.
- `actor(...).validateInput(input[, ValidateInputOptions])` to validate an input against an Actor's
  input schema, matching the reference client.
- `datasets()`/`keyValueStores()` `getOrCreate(name, schema)` overloads that send a creation schema,
  matching the reference client.
- `batchAddRequests` surfaces a non-retryable client error (a 4xx other than 429) as an
  `ApifyApiException`; persistent rate-limit/server failures are returned as `unprocessedRequests`.
- Read-only nested webhook collections (`actor(id).webhooks()`, `task(id).webhooks()`); `create(...)`
  is exposed only on the account-wide `client.webhooks()`, matching the GET-only nested API.
- Single-resource getters return an empty `Optional` when the API responds `200` with `{"data": null}`.
- Public version constants `Version.CLIENT_VERSION` and `Version.API_SPEC_VERSION`.
- Forward-compatible models that capture unmodelled API fields in an `extra` map.
- Offline unit tests (mock HTTP backend) and an integration test suite (one simple GET plus one
  CRUD/complex flow per resource), with runnable, CI-tested documentation examples.
- Language-specific GitHub Actions workflows: `Java integration tests` (Spotless, SpotBugs, build,
  unit, integration, and a standalone `Test examples` step) and a manually triggered `Publish Java
  client` workflow that releases to Maven Central via the Sonatype Central Publisher Portal,
  authenticating with the Maven Central repository credentials from the protected `Publishing`
  GitHub environment, and creates a tagged GitHub release.
