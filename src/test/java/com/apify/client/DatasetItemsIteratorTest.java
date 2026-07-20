package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.dataset.DatasetClient;
import com.apify.client.dataset.DatasetListItemsOptions;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hermetic (token-free) tests for {@link DatasetClient#iterateItems} and its {@code fetchItemsPage}
 * helper — the distinct dataset-items path that parses a bare JSON array body (not a {@code data}
 * envelope) and terminates on an empty page. Driven by a {@link MockTransport}, no {@code
 * APIFY_TOKEN}.
 */
class DatasetItemsIteratorTest {

  private static ApifyClient client(MockTransport backend) {
    return ApifyClient.builder()
        .token("test-token")
        .httpTransport(backend)
        .maxRetries(0)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  @Test
  void pagesBareArrayBodyAndStopsOnEmptyPage() {
    MockTransport backend =
        new MockTransport(
            List.of(
                MockTransport.ok(200, "[{\"n\":1},{\"n\":2}]"),
                MockTransport.ok(200, "[{\"n\":3}]"),
                MockTransport.ok(200, "[]")));
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
  void typedIterateItemsAtDefaultPageSize() {
    // Typed iteration at the server-default page size uses the 3-arg form with a null chunkSize.
    // (No (options, Class<T>) overload exists: with a null second argument, the compiler could not
    // tell it apart from the (options, Long chunkSize) overload — ambiguous overload resolution.)
    // Decodes each item into the requested type.
    MockTransport backend =
        new MockTransport(
            List.of(MockTransport.ok(200, "[{\"n\":1},{\"n\":2}]"), MockTransport.ok(200, "[]")));
    List<Integer> seen = new ArrayList<>();
    Iterator<Row> it =
        client(backend).dataset("d1").iterateItems(new DatasetListItemsOptions(), null, Row.class);
    while (it.hasNext()) {
      seen.add(it.next().n);
    }
    assertEquals(List.of(1, 2), seen, "typed iteration at default page size yields decoded items");
    assertTrue(backend.lastUrl.contains("datasets/d1/items"), backend.lastUrl);
  }

  /** Minimal typed row for the typed-iteration overload test. */
  static final class Row {
    public int n;
  }

  @Test
  void totalCapTrimsDatasetItems() {
    // The cap wins even though the server would return more; only the first page is requested.
    MockTransport backend = MockTransport.ofConstant(200, "[{\"n\":1},{\"n\":2},{\"n\":3}]");
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
    MockTransport backend =
        new MockTransport(
            List.of(MockTransport.ok(200, "[{\"n\":7}]"), MockTransport.ok(200, "[]")));
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
