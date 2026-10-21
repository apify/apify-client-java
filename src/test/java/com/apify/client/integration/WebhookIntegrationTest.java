package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.task.Task;
import com.apify.client.webhook.Webhook;
import com.apify.client.webhook.WebhookClient;
import com.apify.client.webhook.WebhookDispatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebhookIntegrationTest extends IntegrationBase {

  /**
   * A persistent (non-ad-hoc) webhook definition conditioned on the given task. The API rejects a
   * condition that targets a resource that does not exist, and ad hoc webhooks can only target
   * Actor runs. A webhook on a shared Actor would fire on other suites' concurrent runs, so each
   * test conditions its webhook on a task it owns.
   */
  static Map<String, Object> webhookDef(String url, String taskId) {
    return Map.of(
        "isAdHoc",
        false,
        "eventTypes",
        List.of("ACTOR.RUN.SUCCEEDED"),
        "condition",
        Map.of("actorTaskId", taskId),
        "requestUrl",
        url);
  }

  /** Creates a uniquely named task owned by the calling test; the caller deletes it. */
  static Task createOwnedTask(ApifyClient client) {
    return client.tasks().create(TaskIntegrationTest.taskDef(uniqueName("wh-task"))).join();
  }

  @Test
  void listWebhooks() {
    ApifyClient client = requireClient();
    assertTrue(client.webhooks().list(new ListOptions().limit(5L)).join().getTotal() >= 0);
  }

  @Test
  void listWebhookDispatches() {
    ApifyClient client = requireClient();
    assertTrue(client.webhookDispatches().list(new ListOptions().limit(5L)).join().getTotal() >= 0);
  }

  @Test
  void getWebhook() {
    ApifyClient client = requireClient();
    Task task = createOwnedTask(client);
    try {
      Webhook wh =
          client.webhooks().create(webhookDef("https://example.com/webhook", task.getId())).join();
      try {
        var got = client.webhook(wh.getId()).get().join();
        assertTrue(got.isPresent());
        assertEquals(wh.getId(), got.get().getId());
      } finally {
        client.webhook(wh.getId()).delete().join();
      }
    } finally {
      client.task(task.getId()).delete().join();
    }
  }

  @Test
  void getWebhookDispatch() {
    ApifyClient client = requireClient();
    Task task = createOwnedTask(client);
    try {
      Webhook wh =
          client.webhooks().create(webhookDef("https://example.com/webhook", task.getId())).join();
      try {
        WebhookDispatch dispatch = client.webhook(wh.getId()).test().join();
        var got = client.webhookDispatch(dispatch.getId()).get().join();
        assertTrue(got.isPresent());
        assertEquals(dispatch.getId(), got.get().getId());
      } finally {
        client.webhook(wh.getId()).delete().join();
      }
    } finally {
      client.task(task.getId()).delete().join();
    }
  }

  @Test
  void webhookCrudFlow() {
    ApifyClient client = requireClient();
    Task task = createOwnedTask(client);
    try {
      Webhook wh =
          client.webhooks().create(webhookDef("https://example.com/webhook", task.getId())).join();
      try {
        WebhookClient webhook = client.webhook(wh.getId());
        assertTrue(webhook.get().join().isPresent());
        Webhook updated =
            webhook.update(Map.of("requestUrl", "https://example.com/updated")).join();
        assertEquals("https://example.com/updated", updated.getRequestUrl());
        webhook.dispatches().list(new ListOptions()).join();
        webhook.test().join();

        // list() step of the create/get/modify/list/delete flow: verify the just-created webhook
        // appears in the top-level collection listing.
        boolean foundInList =
            pollUntil(
                LIST_FIND_ATTEMPTS,
                LIST_FIND_BACKOFF_MILLIS,
                () ->
                    client
                        .webhooks()
                        .list(new ListOptions().desc(true).limit(10L))
                        .join()
                        .getItems()
                        .stream()
                        .anyMatch(w -> wh.getId().equals(w.getId())));
        assertTrue(
            foundInList, "expected the just-created webhook to appear in the top-level list");

        // Typed getters (previously only reachable via getExtra()): verify the API's response
        // actually deserializes into them, not just that the code compiles.
        assertEquals(task.getId(), wh.getCondition().get("actorTaskId").asText());
        assertTrue(wh.getCreatedAt() != null);
        assertTrue(wh.getModifiedAt() != null);
        assertTrue(wh.getStats() != null);
      } finally {
        client.webhook(wh.getId()).delete().join();
      }
    } finally {
      client.task(task.getId()).delete().join();
    }
  }
}
