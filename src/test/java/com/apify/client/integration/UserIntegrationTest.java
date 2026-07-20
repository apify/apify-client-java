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
  void getUserById() {
    ApifyClient client = requireClient();
    var me = client.me().get();
    assertTrue(me.isPresent());
    // The account's own id is always a valid target for the by-id accessor (public profile view),
    // covering `ApifyClient.user(id)` in addition to the `me()` convenience already exercised
    // above.
    var byId = client.user(me.get().getId()).get();
    assertTrue(byId.isPresent());
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

  // Note: `me().updateLimits(...)` is intentionally NOT exercised by a live integration test. It
  // mutates account-global state that cannot be isolated between the concurrent cross-language test
  // runs the test-requirements mandate (unlike the per-run resources every other test creates), so
  // a
  // mutating test here would race those runs. Its request behaviour is covered offline instead by
  // ClientBehaviourRegressionTest#updateLimitsSendsPutToMeLimits.
}
