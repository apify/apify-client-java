# Changelog

All notable changes to the Apify Java client are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.1] - 2026-07-14

### Added

- Build-time version-drift guard test asserting the Maven project `<version>` (read from a filtered
  build resource) equals `Version.CLIENT_VERSION`, so the two can no longer diverge on a release bump.

### Changed

- Verified the client against OpenAPI specification version `v2-2026-07-13T092445Z` and bumped
  `Version.API_SPEC_VERSION`. The spec change only added already-handled error responses (`402`
  Payment Required on actor and run-resurrect run-sync endpoints, `408` Request Timeout on the
  task run-sync endpoint) and relaxed `required` constraints on run/build/key-value-store/webhook
  stats counters; both are already covered by the client's generic status-code error handling and
  its forward-compatible model deserialization, so no interface or behavior change was needed.

### Fixed

- Integration tests: create-then-iterate collection assertions now tolerate collection LIST-endpoint
  eventual consistency via a bounded retry that rebuilds the iterator between attempts, fixing an
  intermittent `IterationIntegrationTest.iterateRequestQueues` failure.
- Documentation: `docs/examples.md` fragments now match their standalone example files (Store
  iteration output, the named-storage suffix scheme, and the account example's email line).

## [0.3.0] - 2026-07-11

### Added

- `StreamedLog` log-redirection helper (matching the reference client's `getStreamedLog`):
  `RunClient.getStreamedLog()` / `getStreamedLog(StreamedLogOptions)` return a `StreamedLog` that
  follows the run's live log in a background thread and redirects each complete, timestamped message
  to a destination. `StreamedLog` is `AutoCloseable` with `start()`/`stop()` lifecycle. Options:
  `toLog(Consumer<String>)` (custom destination; default is a per-run prefixed `java.util.logging`
  logger), `prefix(String)`, and `fromStart(boolean)` (skip pre-redirection log lines when false).

### Changed

- **Breaking:** `RunClient.getStreamedLog()` now returns a `StreamedLog` redirection helper instead
  of a raw `InputStream`, aligning its public interface with the reference client. For raw stream
  access use `run(id).log().stream(new LogOptions().raw(true))`.
- Standardized the "official, but experimental" disclaimer wording across the README and all
  documentation pages.

### Fixed

- `StreamedLog`: the last complete log message is no longer dropped when stopping a live stream.
  The final flush now runs in a `finally` block, so a stop that unblocks a blocked read with an
  `IOException` still delivers the retained message.
- `StreamedLog`: `stop()`/`close()` can no longer hang. The log stream is now opened in `start()`
  before the reader thread is launched, eliminating a startup race where `stop()` closed a
  still-null stream and then waited forever on a read that never returned.
- Documentation: added `java.util.ArrayList` and `java.util.function.Consumer` to the stated
  snippet import list in `docs/README.md` so the streamed-log example compiles as written.
- `StreamedLog.close()` is now fully idempotent: the running-check and stop happen atomically under
  the monitor, so a double or concurrent `close()` can no longer throw `IllegalStateException`.
- Documentation: `docs/runs.md` streamed-log snippet now uses the `RUN_ID` placeholder, matching the
  convention used across the other snippets.
- `StreamedLog`: a destination consumer that throws no longer escapes as an uncaught exception on the
  background daemon thread. Matching the reference client, the failure is caught, redirection stops,
  and a warning is logged.
- `StreamedLog`: the pending-message buffer is now local to each reader run instead of a shared
  field, so a `start()` racing a still-draining reader can no longer corrupt shared parsing state.
- Documentation: `docs/README.md` now lists `dataset(id).getStatistics()` as returning
  `Optional<JsonNode>`, matching the method table in `docs/storages.md`.

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
- **Breaking:** `StoreCollectionClient.iterate` now takes `iterate(StoreListOptions, Long chunkSize)`,
  where the options' `limit` is the total-items cap and `chunkSize` is the page size. Previously
  `limit` was the per-page size. This aligns Store iteration with the reference client and the new
  collection iterators.

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
