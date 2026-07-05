# Changelog

All notable changes to the Apify Java client are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
