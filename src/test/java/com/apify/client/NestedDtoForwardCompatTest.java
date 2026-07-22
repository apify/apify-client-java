package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.actor.ActorStandby;
import com.apify.client.build.BuildStats;
import com.apify.client.internal.Json;
import com.apify.client.keyvalue.KeyValueStoreKeysPage;
import com.apify.client.requestqueue.RequestQueueOperationInfo;
import com.apify.client.run.ActorRunMeta;
import com.apify.client.run.ActorRunStats;
import com.apify.client.schedule.ScheduleNotifications;
import com.apify.client.store.PricingInfo;
import com.apify.client.task.TaskStats;
import com.apify.client.user.UserProfile;
import com.apify.client.webhook.WebhookStats;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Guards the fix for the nested-value-DTO data-loss finding: every nested value DTO now extends
 * {@link ApifyResource}, so (a) the specific spec fields the finding named as missing typed getters
 * are modelled, and (b) any *other* field the API returns that still has no typed getter is
 * reachable via {@code getExtra()} rather than silently discarded. Each assertion deserializes a
 * literal JSON fixture directly (via the same {@link Json} the client uses for real responses), not
 * just a hand-built object, so a regression that drops {@code extends ApifyResource} or a field
 * name typo is actually caught here, not only at compile time.
 */
class NestedDtoForwardCompatTest {

  @Test
  void actorRunStatsModelsMigrationAndRebootCountAndKeepsUnknownFields() {
    ActorRunStats stats =
        parse(
            "{\"inputBodyLen\":10,\"migrationCount\":3,\"rebootCount\":2,\"restartCount\":1,"
                + "\"resurrectCount\":0,\"memAvgBytes\":1.0,\"memMaxBytes\":2.0,"
                + "\"memCurrentBytes\":3.0,\"cpuAvgUsage\":4.0,\"cpuMaxUsage\":5.0,"
                + "\"cpuCurrentUsage\":6.0,\"netRxBytes\":7,\"netTxBytes\":8,"
                + "\"durationMillis\":9,\"runTimeSecs\":1.5,\"metamorph\":0,"
                + "\"computeUnits\":0.1,\"someFutureStatField\":\"x\"}",
            ActorRunStats.class);

    assertEquals(3L, stats.getMigrationCount());
    assertEquals(2L, stats.getRebootCount());
    assertEquals("x", stats.getExtra().get("someFutureStatField"));
  }

  @Test
  void actorRunMetaModelsScheduleFieldsAndKeepsUnknownFields() {
    ActorRunMeta meta =
        parse(
            "{\"origin\":\"SCHEDULER\",\"clientIp\":\"1.2.3.4\",\"userAgent\":\"curl\","
                + "\"scheduleId\":\"sched123\",\"scheduledAt\":\"2026-07-20T09:00:00.000Z\","
                + "\"someFutureMetaField\":42}",
            ActorRunMeta.class);

    assertEquals("sched123", meta.getScheduleId());
    assertEquals(Instant.parse("2026-07-20T09:00:00.000Z"), meta.getScheduledAt());
    assertEquals(42, meta.getExtra().get("someFutureMetaField"));
  }

  @Test
  void buildStatsModelsImageSizeBytesAndKeepsUnknownFields() {
    BuildStats stats =
        parse(
            "{\"durationMillis\":1000,\"runTimeSecs\":45.7,\"computeUnits\":0.01,"
                + "\"imageSizeBytes\":975770223,\"someFutureStatField\":true}",
            BuildStats.class);

    assertEquals(975770223L, stats.getImageSizeBytes().longValue());
    assertEquals(true, stats.getExtra().get("someFutureStatField"));
  }

  /**
   * Every other nested value DTO in the audit (one representative per resource package) also
   * preserves fields it has no typed getter for, instead of discarding them — the general fix, not
   * just the three fields the finding named explicitly.
   */
  @Test
  void otherAuditedNestedDtosAlsoKeepUnknownFields() {
    assertUnknownFieldPreserved(
        ActorStandby.class, "{\"isEnabled\":true,\"notYetModelled\":\"a\"}", "notYetModelled");
    assertUnknownFieldPreserved(
        ScheduleNotifications.class, "{\"email\":true,\"notYetModelled\":\"b\"}", "notYetModelled");
    assertUnknownFieldPreserved(
        WebhookStats.class, "{\"totalDispatches\":3,\"notYetModelled\":\"c\"}", "notYetModelled");
    assertUnknownFieldPreserved(
        UserProfile.class, "{\"name\":\"a\",\"notYetModelled\":\"d\"}", "notYetModelled");
    assertUnknownFieldPreserved(
        PricingInfo.class,
        "{\"pricingModel\":\"FREE\",\"notYetModelled\":\"e\"}",
        "notYetModelled");
    assertUnknownFieldPreserved(
        TaskStats.class, "{\"totalRuns\":1,\"notYetModelled\":\"f\"}", "notYetModelled");
    assertUnknownFieldPreserved(
        RequestQueueOperationInfo.class,
        "{\"requestId\":\"r1\",\"notYetModelled\":\"g\"}",
        "notYetModelled");
    assertUnknownFieldPreserved(
        KeyValueStoreKeysPage.class,
        "{\"limit\":10,\"isTruncated\":false,\"items\":[],\"notYetModelled\":\"h\"}",
        "notYetModelled");
  }

  @Test
  void paginationListKeepsUnknownFields() {
    String json =
        "{\"total\":1,\"offset\":0,\"limit\":10,\"count\":1,\"desc\":false,"
            + "\"items\":[\"x\"],\"notYetModelled\":\"i\"}";
    PaginationList<String> page =
        Json.parse(
            json.getBytes(StandardCharsets.UTF_8),
            Json.parametric(PaginationList.class, Json.type(String.class)));

    assertTrue(page.getItems().contains("x"));
    assertEquals("i", page.getExtra().get("notYetModelled"));
  }

  private static void assertUnknownFieldPreserved(
      Class<? extends ApifyResource> type, String json, String unknownFieldName) {
    ApifyResource parsed = parse(json, type);
    assertTrue(
        parsed.getExtra().containsKey(unknownFieldName),
        type.getSimpleName() + " should keep unmodelled field \"" + unknownFieldName + "\"");
  }

  private static <T> T parse(String json, Class<T> type) {
    return Json.parse(json.getBytes(StandardCharsets.UTF_8), type);
  }
}
