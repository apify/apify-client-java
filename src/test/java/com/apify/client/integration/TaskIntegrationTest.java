package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.dataset.DatasetListItemsOptions;
import com.apify.client.log.StreamedLogOptions;
import com.apify.client.run.ActorRun;
import com.apify.client.run.RunListOptions;
import com.apify.client.task.Task;
import com.apify.client.task.TaskCallOptions;
import com.apify.client.task.TaskClient;
import com.apify.client.task.TaskStartOptions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class TaskIntegrationTest extends IntegrationBase {

  static Map<String, Object> taskDef(String name) {
    return Map.of(
        "actId",
        "apify/hello-world",
        "name",
        name,
        "description",
        "Integration test task",
        "options",
        Map.of("build", "latest", "memoryMbytes", 256, "timeoutSecs", 60),
        "input",
        Map.of("message", "hello"));
  }

  @Test
  void listTasks() {
    ApifyClient client = requireClient();
    assertTrue(client.tasks().list(new ListOptions().limit(5L)).getTotal() >= 0);
  }

  @Test
  void getTask() {
    ApifyClient client = requireClient();
    Task task = client.tasks().create(taskDef(uniqueName("task-get")));
    try {
      var got = client.task(task.getId()).get();
      assertTrue(got.isPresent());
      assertEquals(task.getId(), got.get().getId());
    } finally {
      client.task(task.getId()).delete();
    }
  }

  @Test
  void taskCrudFlow() {
    ApifyClient client = requireClient();
    Task task = client.tasks().create(taskDef(uniqueName("task-crud")));
    try {
      TaskClient tc = client.task(task.getId());
      assertTrue(tc.get().isPresent());
      tc.updateInput(Map.of("message", "updated"));
      assertTrue(tc.getInput().isPresent());
      tc.update(Map.of("name", uniqueName("task-renamed")));
      tc.runs().list(new ListOptions(), new RunListOptions());

      // list() step of the create/get/modify/list/delete flow: verify the just-created task
      // appears in the top-level collection listing.
      boolean foundInList =
          pollUntil(
              LIST_FIND_ATTEMPTS,
              LIST_FIND_BACKOFF_MILLIS,
              () ->
                  client.tasks().list(new ListOptions().desc(true).limit(10L)).getItems().stream()
                      .anyMatch(t -> task.getId().equals(t.getId())));
      assertTrue(foundInList, "expected the just-created task to appear in the top-level list");

      // Typed getters (previously only reachable via getExtra()): verify the API's response
      // actually deserializes into them, not just that the code compiles.
      assertEquals("Integration test task", task.getDescription());
      assertTrue(task.getStats() != null);
      assertTrue(task.getOptions() != null && "latest".equals(task.getOptions().getBuild()));
      assertTrue(task.getInput() != null);
    } finally {
      client.task(task.getId()).delete();
    }
  }

  @Test
  void taskLastRunAndWebhooks() {
    ApifyClient client = requireClient();
    Task task = client.tasks().create(taskDef(uniqueName("task-lastrun")));
    try {
      TaskClient tc = client.task(task.getId());
      ActorRun run = tc.call(null, new TaskStartOptions(), TEST_ACTOR_WAIT_SECS);
      assertEquals("SUCCEEDED", run.getStatus());
      assertTrue(run.getStats() != null);
      assertTrue(run.getOptions() != null);
      assertTrue(run.getMeta() != null && run.getMeta().getOrigin() != null);

      var lastRun = tc.lastRun("SUCCEEDED").get();
      assertTrue(lastRun.isPresent());
      assertEquals(run.getId(), lastRun.get().getId());

      // Last-run-scoped nested storage GETs (previously untested — only `lastRun().get()` itself
      // was). This task is exclusively owned by this test, so `lastRun` is deterministically the
      // run started above and its default storages are guaranteed to exist.
      tc.lastRun("SUCCEEDED").dataset().listItems(new DatasetListItemsOptions());
      tc.lastRun("SUCCEEDED").keyValueStore().getRecord("OUTPUT");
      assertTrue(tc.lastRun("SUCCEEDED").log().get().isPresent());

      // Read-only nested webhook collection (GET + iterate); no webhooks are registered for this
      // fresh task, so this just exercises both calls succeeding against an empty result.
      assertTrue(tc.webhooks().list(new ListOptions()).getTotal() >= 0);
      assertTrue(!tc.webhooks().iterate(new ListOptions()).hasNext());
    } finally {
      client.task(task.getId()).delete();
    }
  }

  @Test
  void taskCallStreamsLogByDefault() {
    ApifyClient client = requireClient();
    Task task = client.tasks().create(taskDef(uniqueName("task-call-log")));
    try {
      TaskClient tc = client.task(task.getId());
      List<String> collected = new CopyOnWriteArrayList<>();
      // The log-streaming call() overload (TaskCallOptions), matching the reference client's
      // default call(options.log='default') behavior: the run's log is streamed for the duration
      // of the wait without any explicit opt-in.
      ActorRun run =
          tc.call(
              null,
              new TaskCallOptions().logOptions(new StreamedLogOptions().toLog(collected::add)),
              TEST_ACTOR_WAIT_SECS);
      assertEquals("SUCCEEDED", run.getStatus());
      // As in ActorRunIntegrationTest#callWithActorCallOptionsStreamsLogByDefault: call()'s
      // internal log-streaming lifecycle closes the stream as soon as the run finishes, which can
      // race the background reader before it has pulled any bytes off the live log stream, even
      // though the log content is already fully available server-side. Gate the assertion on
      // whether the run actually produced a log at all - via the call()-default variant, which
      // never
      // rescues via a separate explicit stream, so a genuinely broken default-streaming wiring
      // still
      // fails here rather than being masked.
      assertCallDefaultStreamedLogNonEmptyIfProduced(client.run(run.getId()), collected);
    } finally {
      client.task(task.getId()).delete();
    }
  }
}
