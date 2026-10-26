package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.actor.ActorStandby;
import com.apify.client.build.ActorDefinition;
import com.apify.client.build.Build;
import com.apify.client.build.BuildStats;
import com.apify.client.internal.Json;
import com.apify.client.keyvalue.KeyValueStoreKeysPage;
import com.apify.client.requestqueue.RequestQueueOperationInfo;
import com.apify.client.run.ActorRunMeta;
import com.apify.client.run.ActorRunStats;
import com.apify.client.schedule.ScheduleNotifications;
import com.apify.client.store.PricingInfo;
import com.apify.client.task.Task;
import com.apify.client.task.TaskStats;
import com.apify.client.user.UserProfile;
import com.apify.client.webhook.Webhook;
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

  @Test
  void buildActVersionAcceptsNullBuildTag() {
    Build tagged =
        parse(
            "{\"id\":\"b1\",\"actVersion\":{\"sourceType\":\"GIT_REPO\","
                + "\"buildTag\":\"latest\",\"versionNumber\":\"0.1\","
                + "\"gitRepoUrl\":\"https://github.com/apify/x.git\"}}",
            Build.class);
    assertEquals("latest", tagged.getActVersion().getBuildTag());
    assertEquals("0.1", tagged.getActVersion().getVersionNumber());
    assertEquals("GIT_REPO", tagged.getActVersion().getSourceType());
    assertEquals("https://github.com/apify/x.git", tagged.getActVersion().getGitRepoUrl());
    assertTrue(tagged.getActVersion().getSourceFiles().isEmpty());

    Build untagged =
        parse(
            "{\"id\":\"b2\",\"actVersion\":{\"sourceType\":\"SOURCE_FILES\","
                + "\"buildTag\":null,\"versionNumber\":\"0.0\",\"sourceFiles\":["
                + "{\"name\":\"main.js\",\"format\":\"TEXT\",\"content\":\"x\"}]}}",
            Build.class);
    assertNull(untagged.getActVersion().getBuildTag());
    assertEquals(1, untagged.getActVersion().getSourceFiles().size());
    assertEquals("main.js", untagged.getActVersion().getSourceFiles().get(0).getName());
    assertEquals("TEXT", untagged.getActVersion().getSourceFiles().get(0).getFormat());
    assertEquals("x", untagged.getActVersion().getSourceFiles().get(0).getContent());
    assertEquals("SOURCE_FILES", untagged.getActVersion().getSourceType());
  }

  @Test
  void buildActorDefinitionModelsAllFieldsAndAcceptsNull() {
    Build build =
        parse(
            "{\"id\":\"b1\",\"actorDefinition\":{\"actorSpecification\":1,\"name\":\"my-actor\","
                + "\"title\":\"My Actor\",\"description\":\"Does things\",\"version\":\"0.1\","
                + "\"buildTag\":\"latest\",\"meta\":{\"templateId\":\"js-start\","
                + "\"generatedBy\":\"Claude\"},\"environmentVariables\":{\"A\":\"b\"},"
                + "\"dockerfile\":\"./Dockerfile\",\"dockerContextDir\":\"..\","
                + "\"readme\":\"./README.md\",\"input\":{\"type\":\"object\"},"
                + "\"output\":{\"actorOutputSchemaVersion\":1},\"changelog\":null,"
                + "\"storages\":{\"dataset\":{\"views\":{}},\"datasets\":{\"extra\":{\"views\":{}}},"
                + "\"keyValueStore\":{\"actorKeyValueStoreSchemaVersion\":1}},"
                + "\"defaultMemoryMbytes\":\"get(input, 'n', 1) * 1024\",\"minMemoryMbytes\":128,"
                + "\"maxMemoryMbytes\":4096,\"usesStandbyMode\":true,"
                + "\"webServerSchema\":{\"openapi\":\"3.0.0\"},\"webServerMcpPath\":\"/mcp\","
                + "\"someFutureField\":1}}",
            Build.class);

    ActorDefinition def = build.getActorDefinition();
    assertEquals(1, def.getActorSpecification().intValue());
    assertEquals("my-actor", def.getName());
    assertEquals("My Actor", def.getTitle());
    assertEquals("Does things", def.getDescription());
    assertEquals("0.1", def.getVersion());
    assertEquals("latest", def.getBuildTag());
    assertEquals("js-start", def.getMeta().getTemplateId());
    assertEquals("Claude", def.getMeta().getGeneratedBy());
    assertEquals("b", def.getEnvironmentVariables().get("A"));
    assertEquals("./Dockerfile", def.getDockerfile());
    assertEquals("..", def.getDockerContextDir());
    assertEquals("./README.md", def.getReadme());
    assertEquals("object", def.getInput().get("type").asString());
    assertEquals(1, def.getOutput().get("actorOutputSchemaVersion").asInt());
    assertNull(def.getChangelog());
    assertTrue(def.getStorages().getDataset().has("views"));
    assertTrue(def.getStorages().getDatasets().get("extra").has("views"));
    assertEquals(
        1, def.getStorages().getKeyValueStore().get("actorKeyValueStoreSchemaVersion").asInt());
    assertEquals("get(input, 'n', 1) * 1024", def.getDefaultMemoryMbytes().asString());
    assertEquals(128L, def.getMinMemoryMbytes().longValue());
    assertEquals(4096L, def.getMaxMemoryMbytes().longValue());
    assertEquals(true, def.getUsesStandbyMode());
    assertEquals("3.0.0", def.getWebServerSchema().get("openapi").asString());
    assertEquals("/mcp", def.getWebServerMcpPath());
    assertEquals(1, def.getExtra().get("someFutureField"));

    ActorDefinition empty =
        parse("{\"id\":\"b2\",\"actorDefinition\":{}}", Build.class).getActorDefinition();
    assertTrue(empty.getEnvironmentVariables().isEmpty());
    assertNull(empty.getStorages());
    ActorDefinition nulls =
        parse("{\"id\":\"b5\",\"actorDefinition\":{\"input\":null,\"output\":null}}", Build.class)
            .getActorDefinition();
    assertNull(nulls.getInput());
    assertNull(nulls.getOutput());
    assertNull(parse("{\"id\":\"t1\",\"input\":null}", Task.class).getInput());
    assertNull(parse("{\"id\":\"b3\",\"actorDefinition\":null}", Build.class).getActorDefinition());
    assertNull(parse("{\"id\":\"b4\"}", Build.class).getActorDefinition());
  }

  @Test
  void webhookModelsAdHocFlagAndCondition() {
    Webhook adHoc =
        parse(
            "{\"id\":\"w1\",\"isAdHoc\":true,\"condition\":{\"actorRunId\":\"run1\"}}",
            Webhook.class);
    assertTrue(adHoc.isAdHoc());
    assertEquals("run1", adHoc.getCondition().get("actorRunId").asString());

    Webhook persistent =
        parse(
            "{\"id\":\"w2\",\"isAdHoc\":false,\"condition\":{\"actorTaskId\":\"t1\"}}",
            Webhook.class);
    assertEquals("t1", persistent.getCondition().get("actorTaskId").asString());
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
