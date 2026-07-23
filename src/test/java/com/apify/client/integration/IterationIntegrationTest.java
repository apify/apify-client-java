package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.Publishers;
import com.apify.client.StorageListOptions;
import com.apify.client.actor.Actor;
import com.apify.client.actor.ActorEnvVar;
import com.apify.client.actor.ActorListOptions;
import com.apify.client.actor.ActorVersion;
import com.apify.client.build.Build;
import com.apify.client.dataset.Dataset;
import com.apify.client.dataset.DatasetClient;
import com.apify.client.dataset.DatasetListItemsOptions;
import com.apify.client.keyvalue.KeyValueStore;
import com.apify.client.keyvalue.KeyValueStoreClient;
import com.apify.client.keyvalue.KeyValueStoreKey;
import com.apify.client.keyvalue.ListKeysOptions;
import com.apify.client.requestqueue.RequestQueue;
import com.apify.client.run.ActorRun;
import com.apify.client.run.RunListOptions;
import com.apify.client.schedule.Schedule;
import com.apify.client.task.Task;
import com.apify.client.webhook.Webhook;
import com.apify.client.webhook.WebhookDispatch;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Iteration coverage for every paginated collection client that exposes an {@code iterate} helper,
 * mirroring the reference client's async iterators. Where creation is cheap, tests create a few
 * uniquely-named resources and assert iteration finds them (so paging is exercised across pages);
 * for resources whose setup is expensive (builds, runs, dispatches) they iterate a bounded slice
 * and assert the publisher behaves. All resources are uniquely named for parallel isolation.
 */
class IterationIntegrationTest extends IntegrationBase {

  /**
   * Attempts and backoff bounding how long {@link #findsAllEventually} waits for a freshly created
   * resource to surface in its collection listing. Budget: {@code (ATTEMPTS - 1) * BACKOFF} = ~15s.
   */
  private static final int ITER_FIND_ATTEMPTS = 16;

  private static final long ITER_FIND_BACKOFF_MILLIS = 1000L;

  /**
   * Upper bound on items scanned by {@link #findsAll}, purely as a safety net against an infinite
   * scan if a collection endpoint's publisher ever misbehaves (e.g. never completes); no assertion
   * in this suite scans anywhere near this many items. Enforced by requesting one item at a time
   * and cancelling the subscription once the limit is hit, rather than draining unbounded.
   */
  private static final int FIND_ALL_SAFETY_LIMIT = 10_000;

  /**
   * Subscribes to {@code publisher} with one-at-a-time demand, removing each seen id from a copy of
   * {@code targets}, and stops (cancelling the subscription) as soon as every target is found, the
   * publisher completes, or {@link #FIND_ALL_SAFETY_LIMIT} items have been scanned.
   */
  private static <T> boolean findsAll(
      Flow.Publisher<T> publisher, Function<T, String> idOf, Set<String> targets) {
    Set<String> remaining = new HashSet<>(targets);
    CompletableFuture<Void> done = new CompletableFuture<>();
    AtomicInteger scanned = new AtomicInteger();
    publisher.subscribe(
        new Flow.Subscriber<>() {
          private Flow.Subscription subscription;

          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
          }

          @Override
          public void onNext(T item) {
            remaining.remove(idOf.apply(item));
            if (remaining.isEmpty() || scanned.incrementAndGet() >= FIND_ALL_SAFETY_LIMIT) {
              subscription.cancel();
              done.complete(null);
            } else {
              subscription.request(1);
            }
          }

          @Override
          public void onError(Throwable throwable) {
            done.completeExceptionally(throwable);
          }

          @Override
          public void onComplete() {
            done.complete(null);
          }
        });
    done.join();
    return remaining.isEmpty();
  }

  /**
   * Like {@link #findsAll}, but tolerant of collection LIST-endpoint eventual consistency. A
   * resource created through a write endpoint is not always immediately reflected in its
   * collection's LIST response — the write and the list index converge asynchronously on the
   * server, so a create-then-iterate assertion that scans exactly once races that convergence and
   * flakes when the just-created entity has not yet propagated. Delegates the retry/backoff shape
   * to {@link IntegrationBase#pollUntil}, rebuilding a fresh publisher via {@code newPublisher} on
   * every attempt (up to {@link #ITER_FIND_ATTEMPTS} times, sleeping {@link
   * #ITER_FIND_BACKOFF_MILLIS} between attempts) and returning as soon as every target id is seen.
   * An already-consistent account matches on the first pass with no sleeping.
   */
  private static <T> boolean findsAllEventually(
      Supplier<Flow.Publisher<T>> newPublisher, Function<T, String> idOf, Set<String> targets) {
    return pollUntil(
        ITER_FIND_ATTEMPTS,
        ITER_FIND_BACKOFF_MILLIS,
        () -> findsAll(newPublisher.get(), idOf, targets));
  }

  @Test
  void iterateDatasetItems() {
    ApifyClient client = requireClient();
    Dataset ds = client.datasets().getOrCreate(uniqueName("it-ds-items")).join();
    try {
      DatasetClient dataset = client.dataset(ds.getId());
      List<Map<String, Object>> items = new java.util.ArrayList<>();
      for (int i = 1; i <= 5; i++) {
        items.add(Map.of("n", i));
      }
      dataset.pushItems(items).join();

      List<JsonNode> all =
          Publishers.collect(dataset.iterateItems(new DatasetListItemsOptions(), 2L)).join();
      List<Integer> seen = all.stream().map(n -> n.get("n").asInt()).toList();
      assertEquals(List.of(1, 2, 3, 4, 5), seen, "iterateItems should yield all items in order");

      // The total cap trims the tail: limit=3 over 5 items with page size 2 yields exactly 3.
      List<JsonNode> cappedAll =
          Publishers.collect(dataset.iterateItems(new DatasetListItemsOptions().limit(3L), 2L))
              .join();
      List<Integer> capped = cappedAll.stream().map(n -> n.get("n").asInt()).toList();
      assertEquals(List.of(1, 2, 3), capped);
    } finally {
      client.dataset(ds.getId()).delete().join();
    }
  }

  @Test
  void iterateKeyValueStoreKeys() {
    ApifyClient client = requireClient();
    KeyValueStore store = client.keyValueStores().getOrCreate(uniqueName("it-kvs-keys")).join();
    try {
      KeyValueStoreClient kvs = client.keyValueStore(store.getId());
      Set<String> keys = new HashSet<>();
      for (int i = 0; i < 5; i++) {
        String key = "key-" + i;
        kvs.setRecordJson(key, Map.of("i", i)).join();
        keys.add(key);
      }

      List<KeyValueStoreKey> all =
          Publishers.collect(kvs.iterateKeys(new ListKeysOptions())).join();
      Set<String> seen = new HashSet<>();
      for (KeyValueStoreKey k : all) {
        seen.add(k.getKey());
      }
      assertTrue(seen.containsAll(keys), "iterateKeys should yield every key; saw " + seen);

      // The limit caps the total number of keys yielded.
      List<KeyValueStoreKey> capped =
          Publishers.collect(kvs.iterateKeys(new ListKeysOptions().limit(3L))).join();
      assertEquals(3, capped.size(), "limit should cap the keys yielded");
    } finally {
      client.keyValueStore(store.getId()).delete().join();
    }
  }

  @Test
  void iterateDatasets() {
    ApifyClient client = requireClient();
    Set<String> ids = new HashSet<>();
    try {
      for (int i = 0; i < 3; i++) {
        ids.add(client.datasets().getOrCreate(uniqueName("it-ds-" + i)).join().getId());
      }
      // Page size 1 forces the offset publisher across multiple pages; desc puts the fresh ones
      // first.
      assertTrue(
          findsAllEventually(
              () -> client.datasets().iterate(new StorageListOptions().desc(true), 1L),
              Dataset::getId,
              ids),
          "iterate should find every created dataset");
    } finally {
      for (String id : ids) {
        client.dataset(id).delete().join();
      }
    }
  }

  @Test
  void iterateKeyValueStores() {
    ApifyClient client = requireClient();
    Set<String> ids = new HashSet<>();
    try {
      for (int i = 0; i < 2; i++) {
        ids.add(client.keyValueStores().getOrCreate(uniqueName("it-kvs-" + i)).join().getId());
      }
      assertTrue(
          findsAllEventually(
              () -> client.keyValueStores().iterate(new StorageListOptions().desc(true), 1L),
              KeyValueStore::getId,
              ids));
    } finally {
      for (String id : ids) {
        client.keyValueStore(id).delete().join();
      }
    }
  }

  @Test
  void iterateRequestQueues() {
    ApifyClient client = requireClient();
    Set<String> ids = new HashSet<>();
    try {
      for (int i = 0; i < 2; i++) {
        ids.add(client.requestQueues().getOrCreate(uniqueName("it-rq-" + i)).join().getId());
      }
      assertTrue(
          findsAllEventually(
              () -> client.requestQueues().iterate(new StorageListOptions().desc(true), 1L),
              RequestQueue::getId,
              ids));
    } finally {
      for (String id : ids) {
        client.requestQueue(id).delete().join();
      }
    }
  }

  @Test
  void iterateTasks() {
    ApifyClient client = requireClient();
    Set<String> ids = new HashSet<>();
    try {
      for (int i = 0; i < 2; i++) {
        ids.add(
            client
                .tasks()
                .create(TaskIntegrationTest.taskDef(uniqueName("it-task-" + i)))
                .join()
                .getId());
      }
      assertTrue(
          findsAllEventually(
              () -> client.tasks().iterate(new ListOptions().desc(true), 1L), Task::getId, ids));
    } finally {
      for (String id : ids) {
        client.task(id).delete().join();
      }
    }
  }

  @Test
  void iterateSchedules() {
    ApifyClient client = requireClient();
    Set<String> ids = new HashSet<>();
    try {
      for (int i = 0; i < 2; i++) {
        ids.add(
            client
                .schedules()
                .create(ScheduleIntegrationTest.scheduleDef(uniqueName("it-sch-" + i)))
                .join()
                .getId());
      }
      assertTrue(
          findsAllEventually(
              () -> client.schedules().iterate(new ListOptions().desc(true), 1L),
              Schedule::getId,
              ids));
    } finally {
      for (String id : ids) {
        client.schedule(id).delete().join();
      }
    }
  }

  @Test
  void iterateWebhooks() {
    ApifyClient client = requireClient();
    Set<String> ids = new HashSet<>();
    try {
      for (int i = 0; i < 2; i++) {
        ids.add(
            client
                .webhooks()
                .create(WebhookIntegrationTest.webhookDef("https://example.com/it-wh-" + i))
                .join()
                .getId());
      }
      assertTrue(
          findsAllEventually(
              () -> client.webhooks().iterate(new ListOptions().desc(true), 1L),
              Webhook::getId,
              ids));
    } finally {
      for (String id : ids) {
        client.webhook(id).delete().join();
      }
    }
  }

  @Test
  void iterateActors() {
    ApifyClient client = requireClient();
    Set<String> ids = new HashSet<>();
    try {
      for (int i = 0; i < 2; i++) {
        ids.add(
            client
                .actors()
                .create(ActorIntegrationTest.minimalActor(uniqueName("it-act-" + i)))
                .join()
                .getId());
      }
      // Restrict to the current user's Actors so iteration finds the freshly-created ones quickly.
      assertTrue(
          findsAllEventually(
              () -> client.actors().iterate(new ActorListOptions().my(true).desc(true), 1L),
              Actor::getId,
              ids));
    } finally {
      for (String id : ids) {
        client.actor(id).delete().join();
      }
    }
  }

  @Test
  void iterateActorVersionsAndEnvVars() {
    ApifyClient client = requireClient();
    Actor actor =
        client.actors().create(ActorIntegrationTest.minimalActor(uniqueName("it-ver"))).join();
    try {
      var actorClient = client.actor(actor.getId());
      // The versions endpoint is not paginated (one fetch returns every version); fully draining
      // the publisher must terminate and must not re-yield a version. The minimal Actor ships with
      // version 0.0, so iteration yields at least one version, each exactly once.
      List<ActorVersion> versions =
          Publishers.collect(actorClient.versions().iterate(new ListOptions())).join();
      Set<String> versionNumbers = new HashSet<>();
      for (ActorVersion v : versions) {
        versionNumbers.add(v.getVersionNumber());
      }
      assertTrue(versions.size() >= 1, "expected at least the initial version");
      assertEquals(
          versions.size(), versionNumbers.size(), "versions publisher re-yielded a version");
      assertTrue(
          versionNumbers.contains("0.0"), "expected initial version 0.0, saw " + versionNumbers);

      var envVars = actorClient.version("0.0").envVars();
      envVars.create(new ActorEnvVar("IT_VAR_A", "a")).join();
      envVars.create(new ActorEnvVar("IT_VAR_B", "b")).join();
      List<ActorEnvVar> envVarItems = Publishers.collect(envVars.iterate()).join();
      Set<String> seen = new HashSet<>();
      for (ActorEnvVar v : envVarItems) {
        seen.add(v.getName());
      }
      assertTrue(seen.contains("IT_VAR_A") && seen.contains("IT_VAR_B"), "saw " + seen);
    } finally {
      client.actor(actor.getId()).delete().join();
    }
  }

  @Test
  void iterateBuildsBounded() {
    ApifyClient client = requireClient();
    // Builds require building an Actor (expensive); assert a bounded slice iterates cleanly.
    List<Build> builds =
        Publishers.collect(client.builds().iterate(new ListOptions().limit(5L), 2L)).join();
    for (Build build : builds) {
      assertTrue(build.getId() != null);
    }
    assertTrue(
        builds.size() <= 5, "the total-cap limit must bound iteration; got " + builds.size());
  }

  @Test
  void iterateRunsBounded() {
    ApifyClient client = requireClient();
    List<ActorRun> runs =
        Publishers.collect(
                client.runs().iterate(new ListOptions().limit(5L), new RunListOptions(), 2L))
            .join();
    for (ActorRun run : runs) {
      assertTrue(run.getId() != null);
    }
    assertTrue(runs.size() <= 5);
  }

  @Test
  void iterateWebhookDispatchesBounded() {
    ApifyClient client = requireClient();
    List<WebhookDispatch> dispatches =
        Publishers.collect(client.webhookDispatches().iterate(new ListOptions().limit(5L), 2L))
            .join();
    assertTrue(dispatches.size() <= 5);
  }
}
