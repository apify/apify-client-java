package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.schedule.Schedule;
import com.apify.client.schedule.ScheduleClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScheduleIntegrationTest extends IntegrationBase {

  static Map<String, Object> scheduleDef(String name) {
    return Map.of(
        "name",
        name,
        "cronExpression",
        "0 0 * * *",
        "isEnabled",
        false,
        "isExclusive",
        true,
        "actions",
        List.of());
  }

  @Test
  void listSchedules() {
    ApifyClient client = requireClient();
    assertTrue(client.schedules().list(new ListOptions().limit(5L)).join().getTotal() >= 0);
  }

  @Test
  void getSchedule() {
    ApifyClient client = requireClient();
    Schedule sch = client.schedules().create(scheduleDef(uniqueName("sch-get"))).join();
    try {
      var got = client.schedule(sch.getId()).get().join();
      assertTrue(got.isPresent());
      assertEquals(sch.getId(), got.get().getId());
    } finally {
      client.schedule(sch.getId()).delete().join();
    }
  }

  @Test
  void getScheduleLog() {
    ApifyClient client = requireClient();
    Schedule sch = client.schedules().create(scheduleDef(uniqueName("sch-log"))).join();
    try {
      // A fresh schedule has no invocations yet, so the only reachable real-API assertion is an
      // empty (not null/absent) list: the endpoint's "no entries" response must still decode
      // through the {"data": [...]} envelope rather than erroring or resolving to null. Decoding of
      // populated entries (message/level/createdAt) is covered against a real wire-format example
      // in ClientBehaviourRegressionTest#scheduleGetLogDecodesEnvelopeArray — producing a non-empty
      // log here would require waiting for an actual cron invocation, which isn't practical in a
      // short-lived integration test.
      assertEquals(List.of(), client.schedule(sch.getId()).getLog().join());
    } finally {
      client.schedule(sch.getId()).delete().join();
    }
  }

  @Test
  void scheduleCrudFlow() {
    ApifyClient client = requireClient();
    Schedule sch = client.schedules().create(scheduleDef(uniqueName("sch-crud"))).join();
    try {
      ScheduleClient schedule = client.schedule(sch.getId());
      assertTrue(schedule.get().join().isPresent());
      Schedule updated = schedule.update(Map.of("cronExpression", "0 12 * * *")).join();
      assertEquals("0 12 * * *", updated.getCronExpression());
      schedule.getLog().join();
      // list() step of the create/get/modify/list/delete flow.
      assertTrue(client.schedules().list(new ListOptions().limit(5L)).join().getTotal() >= 0);

      // Typed getters (previously only reachable via getExtra()): verify the API's response
      // actually deserializes into them, not just that the code compiles.
      assertTrue(sch.isExclusive());
      assertTrue(sch.getTimezone() != null && !sch.getTimezone().isEmpty());
      assertTrue(sch.getCreatedAt() != null);
      assertTrue(sch.getModifiedAt() != null);
      assertTrue(sch.getNotifications() != null);
      assertTrue(sch.getActions() != null && sch.getActions().isEmpty());
    } finally {
      client.schedule(sch.getId()).delete().join();
    }
  }
}
