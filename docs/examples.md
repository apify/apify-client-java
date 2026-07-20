# Examples

Each example below is a code fragment (not a standalone `main`) that assumes a configured `client`
and the imports listed in the [documentation index](README.md#imports-and-dependencies). The
complete, standalone programs (with imports and a `main`) live under
[`src/test/java/com/apify/client/examples/`](https://github.com/apify/apify-client-java/tree/master/src/test/java/com/apify/client/examples)
and are executed end-to-end against the live API by the `Test examples` CI step (see `ExamplesTest`),
so they are guaranteed to stay runnable. The link points to the GitHub source so it resolves from the
rendered docs as well as the repository.

## Run a store Actor and read its default dataset

```java
ActorRun run = client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L);
PaginationList<JsonNode> items =
    client.dataset(run.getDefaultDatasetId()).listItems(new DatasetListItemsOptions());
System.out.println("Items in this page: " + items.getCount());
```

## Each storage: create, push, read

```java
// Named storages persist on your account; each block deletes its storage in a finally so the
// example does not leak them. A random (not time-based) suffix keeps the names from colliding
// across parallel runs, including two runs starting in the same millisecond.
String suffix = UUID.randomUUID().toString().substring(0, 8);

// Dataset: create, push items, read them back.
Dataset dataset = client.datasets().getOrCreate("java-example-ds-" + suffix);
try {
  client.dataset(dataset.getId()).pushItems(List.of(Map.of("hello", "world")));
  PaginationList<JsonNode> items = client.dataset(dataset.getId()).listItems(new DatasetListItemsOptions());
  System.out.println("Dataset items: " + items.getItems());
} finally {
  client.dataset(dataset.getId()).delete();
}

// Key-value store: create, set a record, read it back.
KeyValueStore store = client.keyValueStores().getOrCreate("java-example-kvs-" + suffix);
try {
  client.keyValueStore(store.getId()).setRecordJson("OUTPUT", Map.of("answer", 42));
  Optional<KeyValueStoreRecord> record = client.keyValueStore(store.getId()).getRecord("OUTPUT");
  record.ifPresent(r -> System.out.println("KVS record bytes: " + r.getValue().length));
} finally {
  client.keyValueStore(store.getId()).delete();
}

// Request queue: create, add a request, read the head.
RequestQueue queue = client.requestQueues().getOrCreate("java-example-rq-" + suffix);
try {
  client.requestQueue(queue.getId()).addRequest(new RequestQueueRequest("https://example.com", "example"), false);
  RequestQueueHead head = client.requestQueue(queue.getId()).listHead(10L);
  System.out.println("Request queue head size: " + head.getItems().size());
} finally {
  client.requestQueue(queue.getId()).delete();
}
```

## Get own account details

```java
Optional<User> user = client.me().get();
user.ifPresent(u -> {
  System.out.println("Account ID: " + u.getId());
  System.out.println("Username:   " + u.getUsername());
  // Fields not modelled on User (email, plan, ...) live in the untyped extras map.
  System.out.println("Email:      " + u.getExtra().get("email"));
});
```

## Create a new Actor, build it, run it, wait, and print the finished run log

```java
Actor created = client.actors().create(Map.of(
    // A random (not time-based) suffix avoids collisions across parallel runs.
    "name", "my-example-actor-" + UUID.randomUUID().toString().substring(0, 8),
    "isPublic", false,
    "versions", List.of(Map.of(
        "versionNumber", "0.0",
        "sourceType", "SOURCE_FILES",
        "buildTag", "latest",
        "sourceFiles", List.of(
            Map.of("name", "Dockerfile", "format", "TEXT",
                   "content", "FROM apify/actor-node:20\nCOPY . ./\nCMD node main.js"),
            Map.of("name", "main.js", "format", "TEXT", "content", "console.log('hi');"))))));
try {
  Build build = client.actor(created.getId()).build("0.0", new ActorBuildOptions());
  client.build(build.getId()).waitForFinish(300L);
  ActorRun run = client.actor(created.getId()).call(null, new ActorStartOptions(), 120L);
  Optional<String> log = client.run(run.getId()).log().get();
  log.ifPresent(System.out::println);
} finally {
  client.actor(created.getId()).delete();
}
```

## Start a run, wait, then fetch the Actor's last run and its storages

```java
client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L);
Optional<ActorRun> last = client.actor("apify/hello-world").lastRun("SUCCEEDED").get();
if (last.isPresent()) {
  ActorRun run = last.get();
  client.dataset(run.getDefaultDatasetId()).listItems(new DatasetListItemsOptions());
  client.keyValueStore(run.getDefaultKeyValueStoreId()).getRecord("OUTPUT");
}
```

## Lazy iteration of Store Actors

```java
Iterator<ActorStoreListItem> it = client.store().iterate(new StoreListOptions().limit(10L), 10L);
int count = 0;
while (count < 5 && it.hasNext()) {
  ActorStoreListItem item = it.next();
  System.out.println((count + 1) + ". " + item.getUsername() + "/" + item.getName());
  count++;
}
```

## Run an Actor with log redirection

`getStreamedLog()` returns a [`StreamedLog`](runs.md#streamed-log-redirection) that follows the
run's live log and redirects each message to a destination (by default a per-run prefixed logger).
It is `AutoCloseable`, so a try-with-resources block stops redirection when the run finishes. The
complete program lives in
[`LogRedirection.java`](https://github.com/apify/apify-client-java/blob/master/src/test/java/com/apify/client/examples/LogRedirection.java).

```java
ActorRun run = client.actor("apify/hello-world").start(null, new ActorStartOptions());
RunClient runClient = client.run(run.getId());
try (StreamedLog streamedLog = runClient.getStreamedLog()) {
  streamedLog.start();
  runClient.waitForFinish(120L);
}
```
