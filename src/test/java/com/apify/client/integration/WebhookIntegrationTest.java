package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.Webhook;
import com.apify.client.WebhookClient;
import com.apify.client.WebhookDispatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebhookIntegrationTest extends IntegrationBase {

  static Map<String, Object> webhookDef(String url) {
    return Map.of(
        "isAdHoc",
        true,
        "eventTypes",
        List.of("ACTOR.RUN.SUCCEEDED"),
        "condition",
        Map.of("actorRunId", "ZZZZZZZZZZZZZZZZZ"),
        "requestUrl",
        url);
  }

  @Test
  void listWebhooks() {
    ApifyClient client = requireClient();
    assertTrue(client.webhooks().list(new ListOptions().limit(5L)).getTotal() >= 0);
  }

  @Test
  void listWebhookDispatches() {
    ApifyClient client = requireClient();
    assertTrue(client.webhookDispatches().list(new ListOptions().limit(5L)).getTotal() >= 0);
  }

  @Test
  void getWebhook() {
    ApifyClient client = requireClient();
    Webhook wh = client.webhooks().create(webhookDef("https://example.com/webhook"));
    try {
      var got = client.webhook(wh.getId()).get();
      assertTrue(got.isPresent());
      assertEquals(wh.getId(), got.get().getId());
    } finally {
      client.webhook(wh.getId()).delete();
    }
  }

  @Test
  void getWebhookDispatch() {
    ApifyClient client = requireClient();
    Webhook wh = client.webhooks().create(webhookDef("https://example.com/webhook"));
    try {
      WebhookDispatch dispatch = client.webhook(wh.getId()).test();
      var got = client.webhookDispatch(dispatch.getId()).get();
      assertTrue(got.isPresent());
      assertEquals(dispatch.getId(), got.get().getId());
    } finally {
      client.webhook(wh.getId()).delete();
    }
  }

  @Test
  void webhookCrudFlow() {
    ApifyClient client = requireClient();
    Webhook wh = client.webhooks().create(webhookDef("https://example.com/webhook"));
    try {
      WebhookClient webhook = client.webhook(wh.getId());
      assertTrue(webhook.get().isPresent());
      Webhook updated = webhook.update(Map.of("requestUrl", "https://example.com/updated"));
      assertEquals("https://example.com/updated", updated.getRequestUrl());
      webhook.dispatches().list(new ListOptions());
      webhook.test();
    } finally {
      client.webhook(wh.getId()).delete();
    }
  }
}
