package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.run.ActorRun;
import com.apify.client.run.RunListOptions;
import com.apify.client.task.Task;
import com.apify.client.task.TaskClient;
import com.apify.client.task.TaskStartOptions;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskIntegrationTest extends IntegrationBase {

  static Map<String, Object> taskDef(String name) {
    return Map.of(
        "actId",
        "apify/hello-world",
        "name",
        name,
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
      ActorRun run = tc.call(null, new TaskStartOptions(), 120L);
      assertEquals("SUCCEEDED", run.getStatus());

      var lastRun = tc.lastRun("SUCCEEDED").get();
      assertTrue(lastRun.isPresent());
      assertEquals(run.getId(), lastRun.get().getId());

      // Read-only nested webhook collection (GET + iterate); no webhooks are registered for this
      // fresh task, so this just exercises both calls succeeding against an empty result.
      assertTrue(tc.webhooks().list(new ListOptions()).getTotal() >= 0);
      assertTrue(!tc.webhooks().iterate(new ListOptions()).hasNext());
    } finally {
      client.task(task.getId()).delete();
    }
  }
}
