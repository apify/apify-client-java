package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.RunListOptions;
import com.apify.client.Task;
import com.apify.client.TaskClient;
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
}
