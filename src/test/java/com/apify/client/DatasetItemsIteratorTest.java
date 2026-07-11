package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hermetic (token-free) tests for {@link DatasetClient#iterateItems} and its {@code fetchItemsPage}
 * helper — the distinct dataset-items path that parses a bare JSON array body (not a {@code data}
 * envelope) and terminates on an empty page. Driven by a {@link MockBackend}, no {@code
 * APIFY_TOKEN}.
 */
class DatasetItemsIteratorTest {

  private static ApifyClient client(MockBackend backend) {
    return ApifyClient.builder()
        .token("test-token")
        .httpBackend(backend)
        .maxRetries(0)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  @Test
  void pagesBareArrayBodyAndStopsOnEmptyPage() {
    MockBackend backend =
        new MockBackend(
            List.of(
                MockBackend.ok(200, "[{\"n\":1},{\"n\":2}]"),
                MockBackend.ok(200, "[{\"n\":3}]"),
                MockBackend.ok(200, "[]")));
    List<Integer> seen = new ArrayList<>();
    Iterator<JsonNode> it =
        client(backend).dataset("d1").iterateItems(new DatasetListItemsOptions(), 2L);
    while (it.hasNext()) {
      seen.add(it.next().get("n").asInt());
    }
    assertEquals(List.of(1, 2, 3), seen, "iterateItems should page the bare-array endpoint");
    assertEquals(3, backend.calls, "two data pages plus the terminating empty page");
    assertTrue(backend.lastUrl.contains("datasets/d1/items"), backend.lastUrl);
    assertTrue(backend.lastUrl.contains("limit=2"), "chunkSize drives the per-request page size");
  }

  @Test
  void totalCapTrimsDatasetItems() {
    // The cap wins even though the server would return more; only the first page is requested.
    MockBackend backend = MockBackend.ofConstant(200, "[{\"n\":1},{\"n\":2},{\"n\":3}]");
    List<Integer> seen = new ArrayList<>();
    Iterator<JsonNode> it =
        client(backend).dataset("d1").iterateItems(new DatasetListItemsOptions().limit(2L), 5L);
    while (it.hasNext()) {
      seen.add(it.next().get("n").asInt());
    }
    assertEquals(List.of(1, 2), seen, "limit caps the total items yielded");
    assertEquals(1, backend.calls);
  }

  /** A typed item for {@link #decodesIntoRequestedType()}. */
  public record Item(int n) {}

  @Test
  void decodesIntoRequestedType() {
    MockBackend backend =
        new MockBackend(List.of(MockBackend.ok(200, "[{\"n\":7}]"), MockBackend.ok(200, "[]")));
    List<Item> seen = new ArrayList<>();
    Iterator<Item> it =
        client(backend).dataset("d1").iterateItems(new DatasetListItemsOptions(), 2L, Item.class);
    while (it.hasNext()) {
      seen.add(it.next());
    }
    assertEquals(1, seen.size(), "typed iteration should decode and yield the item");
    assertEquals(7, seen.get(0).n());
  }
}
