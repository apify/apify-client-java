# Builds

Access the build collection with `client.builds()` (or `client.actor(id).builds()` for an Actor's
builds) and a single build with `client.build(id)`.

## `BuildCollectionClient`

| Method | Description |
|---|---|
| `list(ListOptions)` | List builds. Completes with `PaginationList<Build>`. |
| `iterate(ListOptions, Long chunkSize)` | Lazy `Flow.Publisher<Build>` over all builds; the options' `limit` caps the total yielded (`null`/unset or non-positive = all), `chunkSize` sets the per-request page size (`null` = server default). |

## `BuildClient`

| Method | Description |
|---|---|
| `get()` | Fetch the build. Completes with `Optional<Build>`. |
| `getWithWait(Long waitForFinishSecs)` | Fetch, optionally waiting server-side for the build to finish (clamped to the request timeout; the API caps server-side waiting at 60s). Completes with `Optional<Build>`. |
| `abort()` | Abort the build. Completes with `Build`. |
| `delete()` | Delete the build. |
| `waitForFinish(Long waitSecs)` | Poll until the build finishes (`null` waits indefinitely). Completes with `Build`. |
| `getOpenApiDefinition()` | The build's OpenAPI definition. Completes with `Optional<JsonNode>`. |
| `log()` | A `LogClient` for the build's log. |

`Build` fields: `getId()`, `getActId()`, `getUserId()`, `getStatus()`, `getStartedAt()`,
`getFinishedAt()`, `getBuildNumber()`, `getMeta()` (`BuildMeta` — `getOrigin()`, `getClientIp()`,
`getUserAgent()`), `getStats()` (`BuildStats` — `getDurationMillis()`/`getRunTimeSecs()` as `Long`,
`getComputeUnits()` as `Double`, `getImageSizeBytes()` as `Long`), `getOptions()` (`BuildOptions` —
`getUseCache()`/`getBetaPackages()` as `Boolean`, `getMemoryMbytes()`/`getDiskMbytes()` as `Long`),
`getUsage()` / `getUsageUsd()` (`BuildUsage` — `getActorComputeUnits()` as `Double`, the latter as
its USD cost), `getUsageTotalUsd()` (`Double`), plus `isTerminal()` and `getExtra()`. The status is
one of `READY`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMING-OUT`, `TIMED-OUT`, `ABORTING`, `ABORTED`.
Every nested model above (`BuildMeta`, `BuildStats`, `BuildOptions`, `BuildUsage`) also has its own
`getExtra()`, so a field not yet listed here is still reachable rather than dropped.

```java
Build build = client.actor("me/my-actor").build("0.0", new ActorBuildOptions().tag("latest")).join();
Build finished = client.build(build.getId()).waitForFinish(300L).join();
if (finished.isTerminal()) {
  System.out.println("built " + finished.getBuildNumber());
}
```

`build(versionNumber, options)`'s `versionNumber` (e.g. `"0.0"`) selects *which Actor version's
source* to build, matching one of the version numbers under `client.actor(id).versions()`.
`ActorBuildOptions.tag(...)` is unrelated to that: it is the *build tag* (e.g. `"latest"`, `"beta"`)
stamped onto the resulting build, which is what `ActorStartOptions.build(...)` /
`Actor.defaultBuild(...)` later resolve by name — a version can be rebuilt many times under the
same tag, with each new build replacing which build that tag currently points to.

`ActorBuildOptions` fields (all optional): `betaPackages` (`Boolean`; if `true`, use beta versions
of Apify packages instead of the latest stable ones), `tag` (`String`; the build tag to apply, e.g.
`"latest"` — see above), `useCache` (`Boolean`; whether to reuse the Docker build cache, default
`true`), `waitForFinish` (`Long`; maximum seconds to wait server-side for the build to finish
before the API responds, max 60 — see `defaultBuild` below for the same wait model).

`ActorClient.defaultBuild(Long waitForFinish)` resolves the Actor's currently-tagged default build
(the build behind the `"latest"`/`"default"` tag, i.e. what a plain `start`/`call` with no explicit
`build` would run) and completes with a `BuildClient` handle for it — it does not itself return the
`Build` object. `waitForFinish` only bounds how long the *resolving GET* waits server-side for that build to
finish before responding (`null`/`0` returns immediately with whatever state the build is
currently in); it does not make `defaultBuild` block until the build is done. To actually observe
the finished build, call `.get()` (or `.waitForFinish(...)` for client-side polling) on the returned
handle:

```java
BuildClient defaultBuild = client.actor("me/my-actor").defaultBuild(30L).join();
Build finished = defaultBuild.waitForFinish(300L).join();
```
