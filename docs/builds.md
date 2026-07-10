# Builds

Access the build collection with `client.builds()` (or `client.actor(id).builds()` for an Actor's
builds) and a single build with `client.build(id)`.

## `BuildCollectionClient`

| Method | Description |
|---|---|
| `list(ListOptions)` | List builds. Returns `PaginationList<Build>`. |
| `iterate(ListOptions, Long chunkSize)` | Lazy `Iterator<Build>` over all builds; `limit` caps the total, `chunkSize` sets the page size. |

## `BuildClient`

| Method | Description |
|---|---|
| `get()` | Fetch the build. Returns `Optional<Build>`. |
| `getWithWait(Long waitForFinishSecs)` | Fetch, optionally waiting server-side for the build to finish (clamped to the request timeout; the API caps server-side waiting at 60s). Returns `Optional<Build>`. |
| `abort()` | Abort the build. Returns `Build`. |
| `delete()` | Delete the build. |
| `waitForFinish(Long waitSecs)` | Poll until the build finishes (`null` waits indefinitely). Returns `Build`. |
| `getOpenApiDefinition()` | The build's OpenAPI definition. Returns `Optional<JsonNode>`. |
| `log()` | A `LogClient` for the build's log. |

`Build` fields: `getId()`, `getActId()`, `getStatus()`, `getStartedAt()`, `getFinishedAt()`,
`getBuildNumber()`, plus `isTerminal()` and `getExtra()`. The status is one of `READY`, `RUNNING`,
`SUCCEEDED`, `FAILED`, `TIMING-OUT`, `TIMED-OUT`, `ABORTING`, `ABORTED`.

```java
Build build = client.actor("me/my-actor").build("0.0", new ActorBuildOptions().tag("latest"));
Build finished = client.build(build.getId()).waitForFinish(300L);
if (finished.isTerminal()) {
  System.out.println("built " + finished.getBuildNumber());
}
```

`ActorBuildOptions` fields (all optional): `betaPackages`, `tag`, `useCache`, `waitForFinish`.
