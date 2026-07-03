package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class UserIntegrationTest extends IntegrationBase {

  @Test
  void getOwnAccount() {
    ApifyClient client = requireClient();
    var user = client.me().get();
    assertTrue(user.isPresent());
    assertTrue(user.get().getId() != null && !user.get().getId().isEmpty());
  }

  @Test
  void getMonthlyUsage() {
    ApifyClient client = requireClient();
    JsonNode usage = client.me().monthlyUsage();
    assertTrue(usage != null && !usage.isEmpty());
  }

  @Test
  void getMonthlyUsageForDate() {
    ApifyClient client = requireClient();
    JsonNode usage = client.me().monthlyUsage("2026-06-01");
    assertTrue(usage != null && !usage.isEmpty());
  }

  @Test
  void getLimits() {
    ApifyClient client = requireClient();
    JsonNode limits = client.me().limits();
    assertTrue(limits != null && !limits.isEmpty());
  }
}
