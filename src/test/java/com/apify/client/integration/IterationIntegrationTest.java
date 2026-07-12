package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.Actor;
import com.apify.client.ActorEnvVar;
import com.apify.client.ActorListOptions;
import com.apify.client.ApifyClient;
import com.apify.client.Build;
import com.apify.client.Dataset;
import com.apify.client.DatasetClient;
import com.apify.client.DatasetListItemsOptions;
import com.apify.client.KeyValueStore;
import com.apify.client.KeyValueStoreClient;
import com.apify.client.KeyValueStoreKey;
import com.apify.client.ListKeysOptions;
import com.apify.client.ListOptions;
import com.apify.client.RequestQueue;
import com.apify.client.RunListOptions;
import com.apify.client.Schedule;
import com.apify.client.StorageListOptions;
import com.apify.client.Task;
import com.apify.client.Webhook;
import com.apify.client.WebhookDispatch;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Iteration coverage for every paginated collection client that exposes an {@code iterate} helper,
 * mirroring the reference client's async iterators. Where creation is cheap, tests create a few
 * uniquely-named resources and assert iteration finds them (so paging is exercised across pages);
 * for resources whose setup is expensive (builds, runs, dispatches) they iterate a bounded slice
 * and assert the iterator behaves. All resources are uniquely named for parallel isolation.
 */
class IterationIntegrationTest extends IntegrationBase {

  /** Iterates until every target id is seen (lazily; stops early) or the iterator is exhausted. */
  private static <T> boolean findsAll(
      Iterator<T> it, Function<T, String> idOf, Set<String> targets) {
    Set<String> remaining = new HashSet<>(targets);
    int safety = 0;
    while (it.hasNext() && !remaining.isEmpty() && safety++ < 10000) {
      remaining.remove(idOf.apply(it.next()));
    }
    return remaining.isEmpty();
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
      Iterator<Dataset> it = client.datasets().iterate(new StorageListOptions().desc(true), 1L);
      assertTrue(findsAll(it, Dataset::getId, ids), "iterate should find every created dataset");
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
      Iterator<KeyValueStore> it =
          client.keyValueStores().iterate(new StorageListOptions().desc(true), 1L);
      assertTrue(findsAll(it, KeyValueStore::getId, ids));
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
      Iterator<RequestQueue> it =
          client.requestQueues().iterate(new StorageListOptions().desc(true), 1L);
      assertTrue(findsAll(it, RequestQueue::getId, ids));
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
      Iterator<Task> it = client.tasks().iterate(new ListOptions().desc(true), 1L);
      assertTrue(findsAll(it, Task::getId, ids));
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
      Iterator<Schedule> it = client.schedules().iterate(new ListOptions().desc(true), 1L);
      assertTrue(findsAll(it, Schedule::getId, ids));
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
      Iterator<Webhook> it = client.webhooks().iterate(new ListOptions().desc(true), 1L);
      assertTrue(findsAll(it, Webhook::getId, ids));
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
      Iterator<Actor> it = client.actors().iterate(new ActorListOptions().my(true).desc(true), 1L);
      assertTrue(findsAll(it, Actor::getId, ids));
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
      Iterator<com.apify.client.ActorVersion> versions =
          actorClient.versions().iterate(new ListOptions());
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
    Iterator<com.apify.client.ActorRun> it =
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
