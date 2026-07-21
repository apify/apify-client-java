package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
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
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Iteration coverage for every paginated collection client that exposes an {@code iterate} helper,
 * mirroring the reference client's async iterators. Where creation is cheap, tests create a few
 * uniquely-named resources and assert iteration finds them (so paging is exercised across pages);
 * for resources whose setup is expensive (builds, runs, dispatches) they iterate a bounded slice
 * and assert the iterator behaves. All resources are uniquely named for parallel isolation.
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
   * loop if a collection endpoint's iterator ever misbehaves (e.g. never terminates); no assertion
   * in this suite iterates anywhere near this many items.
   */
  private static final int FIND_ALL_SAFETY_LIMIT = 10_000;

  /** Iterates until every target id is seen (lazily; stops early) or the iterator is exhausted. */
  private static <T> boolean findsAll(
      Iterator<T> it, Function<T, String> idOf, Set<String> targets) {
    Set<String> remaining = new HashSet<>(targets);
    int safety = 0;
    while (it.hasNext() && !remaining.isEmpty() && safety++ < FIND_ALL_SAFETY_LIMIT) {
      remaining.remove(idOf.apply(it.next()));
    }
    return remaining.isEmpty();
  }

  /**
   * Like {@link #findsAll}, but tolerant of collection LIST-endpoint eventual consistency. A
   * resource created through a write endpoint is not always immediately reflected in its
   * collection's LIST response — the write and the list index converge asynchronously on the
   * server, so a create-then-iterate assertion that scans exactly once races that convergence and
   * flakes when the just-created entity has not yet propagated. Delegates the retry/backoff shape
   * to {@link IntegrationBase#pollUntil}, rebuilding a fresh iterator via {@code newIterator} on
   * every attempt (up to {@link #ITER_FIND_ATTEMPTS} times, sleeping {@link
   * #ITER_FIND_BACKOFF_MILLIS} between attempts) and returning as soon as every target id is seen.
   * An already-consistent account matches on the first pass with no sleeping.
   */
  private static <T> boolean findsAllEventually(
      Supplier<Iterator<T>> newIterator, Function<T, String> idOf, Set<String> targets) {
    return pollUntil(
        ITER_FIND_ATTEMPTS,
        ITER_FIND_BACKOFF_MILLIS,
        () -> findsAll(newIterator.get(), idOf, targets));
  }

  @Test
  void iterateDatasetItems() {
    ApifyClient client = requireClient();
    Dataset ds = client.datasets().getOrCreate(uniqueName("it-ds-items"));
    try {
      DatasetClient dataset = client.dataset(ds.getId());
      List<Map<String, Object>> items = new ArrayList<>();
      for (int i = 1; i <= 5; i++) {
        items.add(Map.of("n", i));
      }
      dataset.pushItems(items);

      List<Integer> seen = new ArrayList<>();
      Iterator<JsonNode> it = dataset.iterateItems(new DatasetListItemsOptions(), 2L);
      while (it.hasNext()) {
        seen.add(it.next().get("n").asInt());
      }
      assertEquals(List.of(1, 2, 3, 4, 5), seen, "iterateItems should yield all items in order");

      // The total cap trims the tail: limit=3 over 5 items with page size 2 yields exactly 3.
      List<Integer> capped = new ArrayList<>();
      Iterator<JsonNode> cappedIt =
          dataset.iterateItems(new DatasetListItemsOptions().limit(3L), 2L);
      while (cappedIt.hasNext()) {
        capped.add(cappedIt.next().get("n").asInt());
      }
      assertEquals(List.of(1, 2, 3), capped);
    } finally {
      client.dataset(ds.getId()).delete();
    }
  }

  @Test
  void iterateKeyValueStoreKeys() {
    ApifyClient client = requireClient();
    KeyValueStore store = client.keyValueStores().getOrCreate(uniqueName("it-kvs-keys"));
    try {
      KeyValueStoreClient kvs = client.keyValueStore(store.getId());
      Set<String> keys = new HashSet<>();
      for (int i = 0; i < 5; i++) {
        String key = "key-" + i;
        kvs.setRecordJson(key, Map.of("i", i));
        keys.add(key);
      }

      Set<String> seen = new HashSet<>();
      Iterator<KeyValueStoreKey> it = kvs.iterateKeys(new ListKeysOptions());
      while (it.hasNext()) {
        seen.add(it.next().getKey());
      }
      assertTrue(seen.containsAll(keys), "iterateKeys should yield every key; saw " + seen);

      // The limit caps the total number of keys yielded.
      int count = 0;
      Iterator<KeyValueStoreKey> capped = kvs.iterateKeys(new ListKeysOptions().limit(3L));
      while (capped.hasNext()) {
        capped.next();
        count++;
      }
      assertEquals(3, count, "limit should cap the keys yielded");
    } finally {
      client.keyValueStore(store.getId()).delete();
    }
  }

  @Test
  void iterateDatasets() {
    ApifyClient client = requireClient();
    Set<String> ids = new HashSet<>();
    try {
      for (int i = 0; i < 3; i++) {
        ids.add(client.datasets().getOrCreate(uniqueName("it-ds-" + i)).getId());
      }
      // Page size 1 forces the offset iterator across multiple pages; desc puts the fresh ones
      // first.
      assertTrue(
          findsAllEventually(
              () -> client.datasets().iterate(new StorageListOptions().desc(true), 1L),
              Dataset::getId,
              ids),
          "iterate should find every created dataset");
    } finally {
      for (String id : ids) {
        client.dataset(id).delete();
      }
    }
  }

  @Test
  void iterateKeyValueStores() {
    ApifyClient client = requireClient();
    Set<String> ids = new HashSet<>();
    try {
      for (int i = 0; i < 2; i++) {
        ids.add(client.keyValueStores().getOrCreate(uniqueName("it-kvs-" + i)).getId());
      }
      assertTrue(
          findsAllEventually(
              () -> client.keyValueStores().iterate(new StorageListOptions().desc(true), 1L),
              KeyValueStore::getId,
              ids));
    } finally {
      for (String id : ids) {
        client.keyValueStore(id).delete();
      }
    }
  }

  @Test
  void iterateRequestQueues() {
    ApifyClient client = requireClient();
    Set<String> ids = new HashSet<>();
    try {
      for (int i = 0; i < 2; i++) {
        ids.add(client.requestQueues().getOrCreate(uniqueName("it-rq-" + i)).getId());
      }
      assertTrue(
          findsAllEventually(
              () -> client.requestQueues().iterate(new StorageListOptions().desc(true), 1L),
              RequestQueue::getId,
              ids));
    } finally {
      for (String id : ids) {
        client.requestQueue(id).delete();
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
            client.tasks().create(TaskIntegrationTest.taskDef(uniqueName("it-task-" + i))).getId());
      }
      assertTrue(
          findsAllEventually(
              () -> client.tasks().iterate(new ListOptions().desc(true), 1L), Task::getId, ids));
    } finally {
      for (String id : ids) {
        client.task(id).delete();
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
                .getId());
      }
      assertTrue(
          findsAllEventually(
              () -> client.schedules().iterate(new ListOptions().desc(true), 1L),
              Schedule::getId,
              ids));
    } finally {
      for (String id : ids) {
        client.schedule(id).delete();
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
                .getId());
      }
      assertTrue(
          findsAllEventually(
              () -> client.webhooks().iterate(new ListOptions().desc(true), 1L),
              Webhook::getId,
              ids));
    } finally {
      for (String id : ids) {
        client.webhook(id).delete();
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
        client.actor(id).delete();
      }
    }
  }

  @Test
  void iterateActorVersionsAndEnvVars() {
    ApifyClient client = requireClient();
    Actor actor = client.actors().create(ActorIntegrationTest.minimalActor(uniqueName("it-ver")));
    try {
      var actorClient = client.actor(actor.getId());
      // The versions endpoint is not paginated (one fetch returns every version); fully draining
      // the iterator must terminate and must not re-yield a version. The minimal Actor ships with
      // version 0.0, so iteration yields at least one version, each exactly once.
      Set<String> versionNumbers = new HashSet<>();
      int versionCount = 0;
      Iterator<ActorVersion> versions = actorClient.versions().iterate(new ListOptions());
      while (versions.hasNext()) {
        versionCount++;
        versionNumbers.add(versions.next().getVersionNumber());
      }
      assertTrue(versionCount >= 1, "expected at least the initial version");
      assertEquals(versionCount, versionNumbers.size(), "versions iterator re-yielded a version");
      assertTrue(
          versionNumbers.contains("0.0"), "expected initial version 0.0, saw " + versionNumbers);

      var envVars = actorClient.version("0.0").envVars();
      envVars.create(new ActorEnvVar("IT_VAR_A", "a"));
      envVars.create(new ActorEnvVar("IT_VAR_B", "b"));
      Set<String> seen = new HashSet<>();
      Iterator<ActorEnvVar> it = envVars.iterate();
      while (it.hasNext()) {
        seen.add(it.next().getName());
      }
      assertTrue(seen.contains("IT_VAR_A") && seen.contains("IT_VAR_B"), "saw " + seen);
    } finally {
      client.actor(actor.getId()).delete();
    }
  }

  @Test
  void iterateBuildsBounded() {
    ApifyClient client = requireClient();
    // Builds require building an Actor (expensive); assert a bounded slice iterates cleanly.
    Iterator<Build> it = client.builds().iterate(new ListOptions().limit(5L), 2L);
    int count = 0;
    while (it.hasNext()) {
      assertTrue(it.next().getId() != null);
      count++;
    }
    assertTrue(count <= 5, "the total-cap limit must bound iteration; got " + count);
  }

  @Test
  void iterateRunsBounded() {
    ApifyClient client = requireClient();
    Iterator<ActorRun> it =
        client.runs().iterate(new ListOptions().limit(5L), new RunListOptions(), 2L);
    int count = 0;
    while (it.hasNext()) {
      assertTrue(it.next().getId() != null);
      count++;
    }
    assertTrue(count <= 5);
  }

  @Test
  void iterateWebhookDispatchesBounded() {
    ApifyClient client = requireClient();
    Iterator<WebhookDispatch> it =
        client.webhookDispatches().iterate(new ListOptions().limit(5L), 2L);
    int count = 0;
    while (it.hasNext()) {
      it.next();
      count++;
    }
    assertTrue(count <= 5);
  }
}
