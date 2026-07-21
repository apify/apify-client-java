# Changelog

All notable changes to the Apify Java client are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-07-20

### Changed

- **Breaking:** split the single `com.apify.client` package into resource-scoped sub-packages
  (`com.apify.client.actor`, `.build`, `.run`, `.dataset`, `.keyvalue`, `.requestqueue`, `.task`,
  `.schedule`, `.webhook`, `.user`, `.store`, `.log`, `.http`); `ApifyClient`, its builder, shared
  value types and the exception hierarchy stay in the root package. Pure implementation plumbing
  (`ResourceContext`, `QueryParams`, `HttpClientCore`, `Json`, `Signatures`, `Statuses`,
  `DataEnvelope`, `ApiPaths`, `PaginatedIterator`, ...) moved to a new, non-exported
  `com.apify.client.internal` package; a `module-info.java` exports only the resource-scoped
  packages above.
- **Breaking:** renamed the replaceable transport: `HttpBackend` -> `HttpTransport`,
  `DefaultHttpBackend` -> `DefaultHttpTransport`, `HttpBackend.sendStreaming` ->
  `HttpTransport.sendStreamingResponse`, `ApifyClientBuilder.httpBackend` ->
  `ApifyClientBuilder.httpTransport`.
- **Breaking:** reworked the exception hierarchy: added a common `ApifyClientException` base that
  `ApifyApiException` and the new public `ApifyTransportException` both extend; timeout detection
  is transport-agnostic via a new `HttpTimeoutException` that any `HttpTransport` implementation
  can throw.
- **Breaking:** `ApifyClient.getUserAgent()`/`getApiBaseUrl()` are no longer public.
- **Breaking:** `RunClient.setLastRunParams` is no longer public; last-run construction is now
  internal to `RunClient.lastRun(...)`, used by `ActorClient.lastRun`/`TaskClient.lastRun`.
- `DatasetClient`/`KeyValueStoreClient` are now fully immutable: the public-base-URL is set at
  construction instead of through a self-mutating `withPublicBase` step. `ResourceContext` no
  longer has any in-place-mutating call site.
- Lowered the default HTTP connection-establishment timeout from 30s to 10s and made it
  configurable (`DefaultHttpTransport(Duration)`).
- Consolidated the internal `call`/`callWithHeaders` overloads under a single `call` name.
- Error-response body parsing now maps to typed Jackson classes instead of navigating a raw
  `JsonNode` tree; error-reporting path extraction now parses the URL instead of using string
  offsets.
- Brotli request-body compression is now opt-in: the client depends only on the brotli4j core API
  (`optional`, no native codec bundled); a consumer that wants brotli over the always-available
  gzip fallback adds brotli4j's native artifact for their platform themselves.
- The default log-redirection destination now logs through SLF4J instead of `java.util.logging`.
- Centralized the API's resource-collection path segments (e.g. `datasets`, `actor-builds`) into a
  single `ApiPaths` constants class, referenced by each dedicated resource client instead of being
  passed in from `ApifyClient`.
- Bumped `Version.API_SPEC_VERSION` to `v2-2026-07-20T094852Z`.
- Extracted a shared `AbstractCollectionClient<T, O>` (internal) for the repetitive
  offset/limit `list`/`iterate` boilerplate on `DatasetCollectionClient`,
  `KeyValueStoreCollectionClient`, `RequestQueueCollectionClient`, `ActorCollectionClient`,
  `ScheduleCollectionClient`, `TaskCollectionClient`, `BuildCollectionClient` and
  `RunCollectionClient`; behavior is unchanged.
- Extracted a shared `RunStartSupport` helper (internal) backing `ActorClient`/`TaskClient`'s
  `start`/`call`, removing the duplicated implementation between the two. `ActorClient`/
  `TaskClient` pass their options' behavior in as plain values and method references (e.g.
  `options::apply`), so `ActorStartOptions`/`TaskStartOptions`/`ActorCallOptions`/`TaskCallOptions`
  stay package-private with no public marker interface; behavior is unchanged.
- Tightened `spotbugs-exclude.xml` to only the entries that currently reproduce a real finding.
- `java-integration-tests.yml` now declares an explicit, least-privilege `permissions: contents:
  read` block.
- **Breaking:** moved `ValidateInputOptions` from `com.apify.client.task` to
  `com.apify.client.actor` (its only consumer is `ActorClient.validateInput`); its internal
  `apply`/`contentTypeOrDefault` are package-private again now that it's same-package with that
  consumer.

### Added

- `ApifyClient.setStatusMessage(String, SetStatusMessageOptions)`, matching the reference client's
  top-level `setStatusMessage`.
- `ActorClient.call(Object, ActorCallOptions, Long)` / `TaskClient.call(Object, TaskCallOptions,
  Long)`: a log-streaming `call` overload that streams the run's log for the duration of the wait
  by default, matching the reference client's `call` defaulting `options.log` to `'default'`.
  `ActorCallOptions`/`TaskCallOptions` mirror `ActorStartOptions`/`TaskStartOptions` (minus
  `waitForFinish`) and add `disableLogStreaming()` / `logOptions(StreamedLogOptions)`.
- Typed return values for `RequestQueueClient.listAndLockHead` (`LockedRequestQueueHead`),
  `prolongRequestLock` (`RequestLockInfo`), `unlockRequests` (`UnlockRequestsResult`),
  `batchDeleteRequests` (`BatchDeleteResult`) and `listRequests` (`RequestsList`), replacing raw
  `JsonNode`. `RequestQueueHead`/`RequestQueueRequest` gained `queueModifiedAt`/`lockExpiresAt`.
- Typed getters replacing several `getExtra()`-only fields: `Schedule` (`title`, `timezone`,
  `isExclusive`, `description`, `createdAt`, `modifiedAt`, `nextRunAt`, `lastRunAt`, `actions`,
  `notifications`), `Webhook` (`condition`, `ignoreSslErrors`, `doNotRetry`, `payloadTemplate`,
  `headersTemplate`, `isAdHoc`, `stats`, `description`, `createdAt`, `modifiedAt`,
  `shouldInterpolateStrings`, `lastDispatch`), `Task` (`description`, `stats`, `options`, `input`,
  `actorStandby`), `ActorRun` (`generalAccess`, `chargedEventCounts`, `pricingInfo`, `usage`,
  `usageUsd`, `stats`, `options`, `meta`, `usageTotalUsd`, `buildNumber`, `exitCode`,
  `isContainerServerReady`, `gitBranchName`, `storageIds`).

### Fixed

- `RequestQueueClient.batchAddRequests` now additionally splits chunks by cumulative JSON-encoded
  byte size (matching the reference client's `MAX_PAYLOAD_SIZE_BYTES`), not just the 25-request
  count limit, so a batch of individually large requests (e.g. sizeable `userData`) can no longer
  413.
- `RequestQueueClient.batchAddRequests` no longer throws on a non-retryable 4xx; the affected
  requests are now reported via `BatchAddResult.getUnprocessedRequests()`, matching the reference
  client's never-throws contract.
- `ApifyApiException` now extends `ApifyClientException` (previously `RuntimeException` directly),
  so `catch (ApifyClientException)` catches API errors as documented.
- `PaginationList.getItems()` no longer throws `NullPointerException` when the API response
  contains an explicit `"items": null`.
- `PaginationList.setItems` now defensively copies its input.
- `ApifyClient`'s class javadoc no longer duplicates the "official, but experimental" disclaimer;
  it appears only once, in the top-level README, as required.
- `ApifyClientBuilder.timeout(Duration)` now rejects `Duration.ZERO`/a negative duration at build
  time instead of building a client whose first request fails deep inside the transport.
- `RequestQueueRequest.getUserData()`/`setUserData()` now defensively deep-copy the `JsonNode`,
  matching `getHeaders()`/`getErrorMessages()`, so external mutation of the passed-in/returned value
  can no longer corrupt the request's stored state.
- `RequestQueueClient.paginateRequests(...)` no longer yields more than the requested `totalLimit`
  when a single page overshoots it, matching `PaginatedIterator`'s equivalent guard.
- `Webhook.isShouldInterpolateStrings()` renamed to `getShouldInterpolateStrings()`, following the
  JavaBeans convention for a boxed `Boolean` accessor (`is`-prefixed getters are for primitive
  `boolean`). The underlying field name, `shouldInterpolateStrings`, is unchanged, so this has no
  effect on the JSON wire format.
- `AbstractCollectionClient.list(options)` no longer throws `NullPointerException` on a `null`
  `options` argument (now defaults it, matching `iterate(options)`'s existing tolerance).
- `BatchAddResult.getProcessedRequests()`/`getUnprocessedRequests()` no longer throw
  `NullPointerException` on an explicit JSON `null` for either field.
- `RequestQueueClient.batchAddChunkWithRetries` now also catches `ApifyTransportException` (not
  just `ApifyApiException`), so a persistent transport failure (connection error, timeout) is
  reported via `BatchAddResult.getUnprocessedRequests()` instead of escaping as an exception,
  matching `batchAddRequests`' documented never-throws contract.
- `RunClient.metamorph` now rejects a `null`/empty `targetActorId` with
  `IllegalArgumentException`, matching `charge`'s `eventName` validation.
- `RequestsList.getItems()`, `RequestQueueHead.getItems()`, `LockedRequestQueueHead.getItems()`,
  `KeyValueStoreKeysPage.getItems()`, `Schedule.getActions()` and `Webhook.getEventTypes()` no
  longer throw `NullPointerException` when the API response contains an explicit `null` for that
  field.
- `RequestQueueClient`'s chunk-by-byte-size slicing now accounts for the inter-element commas in
  its incremental size estimate, matching the exact full-array measurement.

### Documentation

- Documented the newly added `ActorRun`/`Webhook` fields above, `ActorRunOptions`/`ActorRunUsage`'s
  getters, and the per-scope support for `RunListOptions.startedAfter`/`startedBefore`; added a
  request-queue lock/unlock worked example (`withClientKey`/`listAndLockHead`/
  `prolongRequestLock`/`deleteRequestLock`/`unlockRequests`); added the missing
  `DownloadItemsFormat` import to the package table and fixed the Quick Start "fragment"
  contradiction; documented `ApifyApiException`'s accessor return types and the
  `HttpTimeoutException` naming collision with `java.net.http.HttpTimeoutException`; added
  `setStatusMessage` to the README resources table; noted `getExtra()` on every thin response model.
- Fixed a contradiction in `README.md`/`docs/README.md`: both described `docs/examples.md` as
  "complete, runnable programs," but its own snippets are fragments in the same style as the
  resource pages — the complete, runnable programs live under
  `src/test/java/com/apify/client/examples/`. Reworded both to match.
  Added the missing `WebhookLastDispatch` import to the `com.apify.client.webhook` package-table row.
  Corrected the "one raw-JSON field" framing in `docs/README.md`'s "Raw JSON values" section:
  `ActorRun` actually carries two (`getPricingInfo()`, `getStorageIds()`), and `Task.getInput()` (the
  model field) is a third, distinct from the live-fetching `task(id).getInput()` client call.
  Clarified in `docs/storages.md` that request queues, unlike datasets/key-value stores, have no
  `getOrCreate(String, Object schema)` overload. Noted that
  `SetStatusMessageOptions.isStatusMessageTerminal(boolean)` intentionally takes a primitive
  `boolean`, not boxed `Boolean` like this client's other optional option fields. Documented the
  `DefaultHttpTransport(Duration)` constructor in the README's transport section, and disclosed
  `Duration`'s package (`java.time.Duration`) next to the Configuration snippet for consistency with
  how other fragments disclose the types they use. Added a javadoc caveat on
  `RequestQueueClient.batchAddRequests`/`RequestQueueRequest.getUniqueKey()`: retry reconciliation
  matches by `uniqueKey`, so a request that omits it can be falsely reported unprocessed after
  retries even though it succeeded server-side.
- Removed a stale `docs/actors.md` cross-package import note for `ValidateInputOptions`, now moot
  since it moved to `com.apify.client.actor`; updated `docs/README.md`'s package table to match.
  Documented `ActorStandby`'s fields in `docs/tasks.md`, added the standard `getExtra()` note to
  `WebhookDispatch` (`docs/webhooks.md`) and `ActorStoreListItem` (`docs/misc.md`), added a
  `ListKeysOptions` field list to `docs/storages.md`, and made `ActorRunStats`'s field list in
  `docs/runs.md` exhaustive while noting it has no `getExtra()` fallback. Reworded `ApiResponse`'s
  javadoc (it is a plain public data carrier used across resource clients, not internal plumbing)
  and corrected `spotbugs-exclude.xml`'s header to accurately explain why its former `ApiResponse`
  EI_EXPOSE exclusion stopped reproducing (the no-copy contract never changed; the tool simply
  stopped flagging it) rather than implying the exposure was defensively fixed.

## [0.3.1] - 2026-07-14

### Added

- Build-time version-drift guard test asserting the Maven project `<version>` (read from a filtered
  build resource) equals `Version.CLIENT_VERSION`, so the two can no longer diverge on a release bump.

### Changed

- Bumped `Version.API_SPEC_VERSION` to `v2-2026-07-13T092445Z`.

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

- Bumped `Version.API_SPEC_VERSION` to `v2-2026-07-10T105921Z`.
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

- Bumped `Version.API_SPEC_VERSION` to `v2-2026-07-08T143931Z`.
- Aligned the `User-Agent` OS token with the reference JS client's `os.platform()` token: it now
  uses the short, lowercase platform identifier (`linux`, `darwin`, `win32`, `android`, …) instead
  of the human-readable `os.name` value.

### Added

- Request bodies of 1024 bytes or more are now gzip-compressed and sent with
  `Content-Encoding: gzip`.

## [0.1.1] - 2026-07-07

### Changed

- Bumped `Version.API_SPEC_VERSION` to `v2-2026-07-07T132551Z`.
- Corrected the `LastRunOptions` documentation: `origin` is now a spec-declared query parameter on
  the `runs/last` endpoints (behaviour unchanged).

## [0.1.0] - 2026-07-03

Initial release of the Java client for the Apify API, targeting OpenAPI specification version
`v2-2026-07-02T131926Z`.

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
