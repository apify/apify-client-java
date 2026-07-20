# Actors, versions & environment variables

Access the Actor collection with `client.actors()` and a single Actor with `client.actor(id)`,
where `id` is an Actor ID or `username~name` (a `/` in the id is accepted and normalized).

## `ActorCollectionClient`

| Method | Description |
|---|---|
| `list(ActorListOptions)` | List the account's Actors. Returns `PaginationList<Actor>`. |
| `iterate(ActorListOptions, Long chunkSize)` | Lazy `Iterator<Actor>` over all matches; the options' `limit` caps the total yielded (`null`/unset or non-positive = all), `chunkSize` sets the page size (`null` = server default). |
| `create(Object)` | Create a new Actor from a JSON-serializable definition. Returns `Actor`. |

`ActorListOptions` adds `my(Boolean)` (only Actors owned by the current user) and
`sortBy(String)` (e.g. `createdAt`, `stats.lastRunStartedAt`) on top of the standard offset/limit/desc.

```java
PaginationList<Actor> mine = client.actors().list(new ActorListOptions().my(true).limit(5L));
for (Actor actor : mine.getItems()) {
  System.out.println(actor.getName());
}
```

Create an Actor with a `SOURCE_FILES` version (`sourceType` is one of `SOURCE_FILES`, `GIT_REPO`,
`TARBALL`, `GITHUB_GIST`, `SOURCE_CODE`; a source-file `format` is `TEXT` or `BASE64`):

```java
Actor created = client.actors().create(Map.of(
    "name", "my-actor",
    "isPublic", false,
    "versions", List.of(Map.of(
        "versionNumber", "0.0",
        "sourceType", "SOURCE_FILES",
        "buildTag", "latest",
        "sourceFiles", List.of(
            Map.of("name", "Dockerfile", "format", "TEXT",
                   "content", "FROM apify/actor-node:20\nCOPY . ./\nCMD node main.js"),
            Map.of("name", "main.js", "format", "TEXT",
                   "content", "console.log('hi');"))))));
```

## `ActorClient`

| Method | Description |
|---|---|
| `get()` | Fetch the Actor. Returns `Optional<Actor>`. |
| `update(Object)` | Update the Actor with the given fields. Returns `Actor`. |
| `delete()` | Delete the Actor. |
| `start(Object input, ActorStartOptions)` | Start a run, returning immediately. Returns `ActorRun`. |
| `call(Object input, ActorStartOptions, Long waitSecs)` | Start a run and poll until it finishes (`null` waits indefinitely). Returns `ActorRun`. |
| `validateInput(Object input)` / `validateInput(Object input, ValidateInputOptions)` | Validate an input against the Actor's input schema. Returns `boolean`. `ValidateInputOptions` fields (optional): `build`, `contentType`. |
| `build(String versionNumber, ActorBuildOptions)` | Build a version. Returns `Build`. |
| `defaultBuild(Long waitForFinish)` | Resolve the default build. Returns `BuildClient`. |
| `lastRun(String status)` / `lastRun(LastRunOptions)` | A `RunClient` for the last run. |
| `builds()` / `runs()` / `versions()` | Nested collection clients. |
| `webhooks()` | Read-only nested webhook collection (`NestedWebhookCollectionClient`, `list` + `iterate`, no `create`). |
| `version(String)` | An `ActorVersionClient`. |

`ActorStartOptions` fields (all optional): `build` (the tag or number of the build to run, e.g.
`latest`, `0.1.2`), `memoryMbytes` (memory in megabytes allocated for the run), `timeoutSecs` (run
timeout in seconds; `0` means no timeout), `waitForFinish` (maximum seconds to wait server-side for
the run to finish, max 60), `maxItems` (maximum dataset items to charge, pay-per-result Actors),
`maxTotalChargeUsd` (maximum total charge in USD, pay-per-event Actors), `contentType` (content type
of the input body, defaults to `application/json`), `restartOnError` (restart the run if it fails),
`forcePermissionLevel` (override the Actor's permission level for this run:
`LIMITED_PERMISSIONS`/`FULL_PERMISSIONS`), and `webhooks(List<Object>)` — ad-hoc webhook definitions
(each a JSON-serializable `Map`, as in [Webhooks](webhooks.md)) that the client base64-encodes on the
wire.

`lastRun(String status)` filters only by status; `lastRun(LastRunOptions)` also accepts an origin
filter. `LastRunOptions` has fluent setters `status(String)` (e.g. `SUCCEEDED`, `RUNNING`) and
`origin(String)` (e.g. `API`, `WEB`, `SCHEDULER`); leave a setter uncalled to omit that filter.

```java
Optional<ActorRun> last =
    client.actor("apify/hello-world").lastRun(new LastRunOptions().status("SUCCEEDED").origin("API")).get();
```

```java
ActorRun run = client.actor("apify/hello-world")
    .call(Map.of("greeting", "hi"), new ActorStartOptions().memoryMbytes(512L), 120L);
System.out.println(run.getStatus());
```

`Actor` fields: `getId()`, `getUserId()`, `getName()`, `getUsername()`, `getTitle()`,
`getDescription()`, `isPublic()`, `getCreatedAt()`, `getModifiedAt()`, plus `getExtra()` for any
unmodelled fields.

## `ActorVersionClient` and `ActorVersionCollectionClient`

`client.actor(id).versions()` lists/creates versions; `client.actor(id).version(v)` reads, updates
and deletes a single version and exposes its environment variables.

### `ActorVersionCollectionClient` — `client.actor(id).versions()`

| Method | Description |
|---|---|
| `list(ListOptions)` | List the Actor's versions. Returns `PaginationList<ActorVersion>`. The endpoint takes no query parameters; `options` (`offset`/`limit`/`desc`) is accepted only for signature consistency with every other `list(ListOptions)` and has no effect — pass `null`. |
| `iterate(ListOptions)` | Lazy `Iterator<ActorVersion>` over all versions; `limit` caps the total (`null`/unset or non-positive = all), applied client-side after the single fetch. `offset` has no effect and there is no page size to tune. |
| `create(Object version)` | Create a version. Returns `ActorVersion`. |

### `ActorVersionClient` — `client.actor(id).version(v)`

| Method | Description |
|---|---|
| `get()` | Fetch the version. Returns `Optional<ActorVersion>`. |
| `update(Object)` | Update the version. Returns `ActorVersion`. |
| `delete()` | Delete the version. |
| `envVars()` | An `ActorEnvVarCollectionClient` for the version's environment variables. |
| `envVar(String name)` | An `ActorEnvVarClient` for a single environment variable. |

```java
ActorVersion version = client.actor("me/my-actor").version("0.0").get().orElseThrow();
System.out.println(version.getSourceType());
```

`ActorVersion` fields: `getVersionNumber()`, `getSourceType()`, plus `getExtra()` for any unmodelled
fields.

## `ActorEnvVarClient` and `ActorEnvVarCollectionClient`

Attach environment variables to a version. `ActorEnvVar` has a `(name, value)` constructor plus
fluent setters `setName`, `setValue`, `setIsSecret(Boolean)` (when secret, the value is stored
encrypted), and matching getters `getName()`, `getValue()`, `getIsSecret()`.

### `ActorEnvVarCollectionClient` — `client.actor(id).version(v).envVars()`

| Method | Description |
|---|---|
| `list()` | List the version's environment variables. Returns `PaginationList<ActorEnvVar>`. |
| `iterate()` | `Iterator<ActorEnvVar>` over the variables. The env-var collection is not paginated (all variables are returned at once), so this iterates a single fetched page; provided for API consistency. |
| `create(ActorEnvVar)` | Create an environment variable. Returns `ActorEnvVar`. |

### `ActorEnvVarClient` — `client.actor(id).version(v).envVar(name)`

| Method | Description |
|---|---|
| `get()` | Fetch the environment variable. Returns `Optional<ActorEnvVar>`. |
| `update(ActorEnvVar)` | Update the environment variable. Returns `ActorEnvVar`. |
| `delete()` | Delete the environment variable. |

```java
client.actor("me/my-actor").version("0.0").envVars()
    .create(new ActorEnvVar("API_KEY", "secret").setIsSecret(true));
Optional<ActorEnvVar> ev = client.actor("me/my-actor").version("0.0").envVar("API_KEY").get();
```
