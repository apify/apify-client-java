package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.dataset.DatasetListItemsOptions;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * {@code RunClient.iterateDatasetItems}: iterating a run's dataset while the run is still producing
 * items, against a scripted run and dataset.
 */
class RunDatasetItemsTest {

  /** A fake run + dataset: {@code itemCount} grows by one batch per run poll. */
  private static final class FakeRun {
    final List<String> rows = new ArrayList<>();
    final List<String> requests = new ArrayList<>();
    int itemCountReported;
    String status = "RUNNING";
    final AtomicInteger runGets = new AtomicInteger();
    // Rows made visible per poll of the run, then the run finishes with `lagging` rows not yet
    // counted.
    final int rowsPerPoll;
    final int totalRows;
    final int laggingAtFinish;

    FakeRun(int totalRows, int rowsPerPoll, int laggingAtFinish) {
      this.totalRows = totalRows;
      this.rowsPerPoll = rowsPerPoll;
      this.laggingAtFinish = laggingAtFinish;
      for (int i = 0; i < totalRows; i++) {
        rows.add("{\"n\":" + i + "}");
      }
    }

    /** Replaces the first {@code count} rows with empty items. */
    FakeRun withEmptyHead(int count) {
      for (int i = 0; i < count; i++) {
        rows.set(i, "{}");
      }
      return this;
    }

    MockTransport.Scripted answer(HttpRequest request) {
      URI uri = request.uri();
      String path = uri.getPath();
      String query = uri.getQuery() == null ? "" : uri.getQuery();
      requests.add(path + (query.isEmpty() ? "" : "?" + query));
      if (path.endsWith("/actor-runs/r1")) {
        int poll = runGets.incrementAndGet();
        // The first get resolves the run; each later poll reveals another batch, then finishes.
        int visible = Math.min(poll * rowsPerPoll, totalRows);
        boolean done = visible >= totalRows;
        itemCountReported = done ? totalRows - laggingAtFinish : visible;
        status = done ? "SUCCEEDED" : "RUNNING";
        return MockTransport.ok(200, "{\"data\":{\"id\":\"r1\",\"status\":\"" + status + "\"}}");
      }
      if (path.endsWith("/actor-runs/r1/dataset")) {
        return MockTransport.ok(
            200, "{\"data\":{\"id\":\"d1\",\"itemCount\":" + itemCountReported + "}}");
      }
      if (path.endsWith("/actor-runs/r1/dataset/items")) {
        Map<String, String> params = new java.util.HashMap<>();
        for (String pair : query.split("&")) {
          String[] kv = pair.split("=", 2);
          params.put(kv[0], kv.length > 1 ? kv[1] : "");
        }
        int offset = Integer.parseInt(params.getOrDefault("offset", "0"));
        int limit = Integer.parseInt(params.getOrDefault("limit", "1000"));
        int end = Math.min(totalRows, offset + limit);
        List<String> scanned = offset >= end ? List.of() : rows.subList(offset, end);
        // skipEmpty drops the empty rows from what is returned, but they were still scanned.
        List<String> page =
            "1".equals(params.get("skipEmpty"))
                ? scanned.stream().filter(row -> !row.equals("{}")).toList()
                : scanned;
        return MockTransport.ok(
            200,
            "[" + String.join(",", page) + "]",
            Map.of("X-Apify-Pagination-Count", String.valueOf(scanned.size())));
      }
      return MockTransport.ok(404, "{\"error\":{\"type\":\"record-not-found\",\"message\":\"x\"}}");
    }
  }

  private static ApifyClient client(Function<HttpRequest, MockTransport.Scripted> router) {
    return ApifyClient.builder()
        .token("t")
        .httpTransport(MockTransport.ofRouter(router))
        .maxRetries(0)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  private static List<Integer> ns(List<JsonNode> items) {
    return items.stream().map(n -> n.get("n").asInt()).toList();
  }

  private static List<Integer> range(int fromInclusive, int toExclusive) {
    List<Integer> out = new ArrayList<>();
    for (int i = fromInclusive; i < toExclusive; i++) {
      out.add(i);
    }
    return out;
  }

  @Test
  void readsRowsWhileTheRunProducesThemAndTheTailAfterItFinishes() {
    // 7 rows, 3 become visible per poll; when the run finishes the counter still lags by 2 rows.
    FakeRun fake = new FakeRun(7, 3, 2);
    List<JsonNode> items =
        Publishers.collect(
                client(fake::answer)
                    .run("r1")
                    .iterateDatasetItems(new DatasetListItemsOptions(), 2L, 0L, JsonNode.class))
            .join();
    assertEquals(range(0, 7), ns(items), "every row exactly once, in order");
  }

  @Test
  void aFinishedRunIsReadInPagesOfTheChunkSize() {
    FakeRun fake = new FakeRun(5, 10, 0);
    List<JsonNode> items =
        Publishers.collect(
                client(fake::answer)
                    .run("r1")
                    .iterateDatasetItems(new DatasetListItemsOptions(), 2L, 0L, JsonNode.class))
            .join();
    assertEquals(range(0, 5), ns(items));
    long itemRequests = fake.requests.stream().filter(r -> r.contains("/dataset/items")).count();
    assertTrue(itemRequests >= 3, "5 rows in pages of 2 need at least 3 item requests");
    assertTrue(
        fake.requests.stream()
            .filter(r -> r.contains("/dataset/items"))
            .anyMatch(r -> r.contains("limit=2")),
        fake.requests.toString());
  }

  @Test
  void offsetAndLimitBoundTheRowsThatAreRead() {
    FakeRun fake = new FakeRun(20, 20, 0);
    List<JsonNode> items =
        Publishers.collect(
                client(fake::answer)
                    .run("r1")
                    .iterateDatasetItems(
                        new DatasetListItemsOptions().offset(4L).limit(6L), 4L, 0L, JsonNode.class))
            .join();
    assertEquals(range(4, 10), ns(items));
  }

  @Test
  void anEmptyDatasetEndsTheIterationOnceTheRunHasFinished() {
    FakeRun fake = new FakeRun(0, 1, 0);
    List<JsonNode> items =
        Publishers.collect(client(fake::answer).run("r1").iterateDatasetItems(null)).join();
    assertTrue(items.isEmpty());
  }

  @Test
  void orderIsNeverReversedByDesc() {
    FakeRun fake = new FakeRun(4, 4, 0);
    List<JsonNode> items =
        Publishers.collect(
                client(fake::answer)
                    .run("r1")
                    .iterateDatasetItems(new DatasetListItemsOptions().desc(true)))
            .join();
    assertEquals(range(0, 4), ns(items));
    assertTrue(
        fake.requests.stream().noneMatch(r -> r.contains("desc=")), fake.requests.toString());
  }

  @Test
  void theTailKeepsReadingPastRowsAFilterEmptiedAndLaggingCounts() {
    // Two empty rows, then two with data; the run has finished but the dataset's itemCount still
    // reads 0, so every row is read by the tail. skipEmpty empties the first page entirely, which
    // must not end the iteration: the next row is probed without the filter.
    FakeRun fake = new FakeRun(4, 4, 4).withEmptyHead(2);
    List<JsonNode> items =
        Publishers.collect(
                client(fake::answer)
                    .run("r1")
                    .iterateDatasetItems(
                        new DatasetListItemsOptions().skipEmpty(true), 2L, 0L, JsonNode.class))
            .join();
    assertEquals(List.of(2, 3), ns(items));
  }

  @Test
  void aLastRunClientStaysOnTheRunItFirstResolvedTo() {
    FakeRun fake = new FakeRun(4, 2, 0);
    AtomicInteger lastCalls = new AtomicInteger();
    List<String> paths = new ArrayList<>();
    Function<HttpRequest, MockTransport.Scripted> router =
        request -> {
          String path = request.uri().getPath();
          paths.add(path);
          if (path.endsWith("/actors/a/runs/last")) {
            String id = lastCalls.incrementAndGet() == 1 ? "r1" : "r2";
            return MockTransport.ok(
                200, "{\"data\":{\"id\":\"" + id + "\",\"status\":\"RUNNING\"}}");
          }
          return fake.answer(request);
        };
    List<JsonNode> items =
        Publishers.collect(
                client(router)
                    .actor("a")
                    .lastRun(new com.apify.client.run.LastRunOptions())
                    .iterateDatasetItems(new DatasetListItemsOptions(), 2L, 0L, JsonNode.class))
            .join();

    assertEquals(range(0, 4), ns(items));
    assertTrue(
        paths.stream()
            .filter(p -> !p.endsWith("/runs/last"))
            .allMatch(p -> p.contains("/actor-runs/r1")),
        paths.toString());
    assertTrue(paths.stream().noneMatch(p -> p.contains("r2")), paths.toString());
  }
}
