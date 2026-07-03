package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.ListOptions;
import com.apify.client.Schedule;
import com.apify.client.ScheduleClient;
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
    assertTrue(client.schedules().list(new ListOptions().limit(5L)).getTotal() >= 0);
  }

  @Test
  void getSchedule() {
    ApifyClient client = requireClient();
    Schedule sch = client.schedules().create(scheduleDef(uniqueName("sch-get")));
    try {
      var got = client.schedule(sch.getId()).get();
      assertTrue(got.isPresent());
      assertEquals(sch.getId(), got.get().getId());
    } finally {
      client.schedule(sch.getId()).delete();
    }
  }

  @Test
  void getScheduleLog() {
    ApifyClient client = requireClient();
    Schedule sch = client.schedules().create(scheduleDef(uniqueName("sch-log")));
    try {
      // Simple GET on the schedule-log endpoint; a fresh schedule may have no log yet (empty
      // Optional), which is a valid result — we only assert the call itself succeeds.
      client.schedule(sch.getId()).getLog();
    } finally {
      client.schedule(sch.getId()).delete();
    }
  }

  @Test
  void scheduleCrudFlow() {
    ApifyClient client = requireClient();
    Schedule sch = client.schedules().create(scheduleDef(uniqueName("sch-crud")));
    try {
      ScheduleClient schedule = client.schedule(sch.getId());
      assertTrue(schedule.get().isPresent());
      Schedule updated = schedule.update(Map.of("cronExpression", "0 12 * * *"));
      assertEquals("0 12 * * *", updated.getCronExpression());
      schedule.getLog();
    } finally {
      client.schedule(sch.getId()).delete();
    }
  }
}
