package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.dataset.DatasetClient;
import com.apify.client.dataset.DatasetListItemsOptions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

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

  private static <T> List<T> collect(Flow.Publisher<T> publisher) {
    return Publishers.collect(publisher).join();
  }

  @Test
  void pagesBareArrayBodyAndStopsOnEmptyPage() {
    MockTransport backend =
        new MockTransport(
            List.of(
                MockTransport.ok(200, "[{\"n\":1},{\"n\":2}]"),
                MockTransport.ok(200, "[{\"n\":3}]"),
                MockTransport.ok(200, "[]")));
    List<JsonNode> seen =
        collect(client(backend).dataset("d1").iterateItems(new DatasetListItemsOptions(), 2L));
    List<Integer> ns = seen.stream().map(n -> n.get("n").asInt()).toList();
    assertEquals(List.of(1, 2, 3), ns, "iterateItems should page the bare-array endpoint");
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
    List<Row> seen =
        collect(
            client(backend)
                .dataset("d1")
                .iterateItems(new DatasetListItemsOptions(), null, Row.class));
    List<Integer> ns = seen.stream().map(row -> row.n).toList();
    assertEquals(List.of(1, 2), ns, "typed iteration at default page size yields decoded items");
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
    List<JsonNode> seen =
        collect(
            client(backend)
                .dataset("d1")
                .iterateItems(new DatasetListItemsOptions().limit(2L), 5L));
    List<Integer> ns = seen.stream().map(n -> n.get("n").asInt()).toList();
    assertEquals(List.of(1, 2), ns, "limit caps the total items yielded");
    assertEquals(1, backend.calls);
  }

  /** A typed item for {@link #decodesIntoRequestedType()}. */
  public record Item(int n) {}

  @Test
  void listItemsExposesScannedCountFromHeader() {
    MockTransport backend =
        new MockTransport(
            List.of(MockTransport.ok(200, "[{\"n\":1}]", Map.of("X-Apify-Pagination-Count", "5"))));
    PaginationList<JsonNode> page =
        client(backend).dataset("d1").listItems(new DatasetListItemsOptions()).join();
    assertEquals(1, page.getCount(), "count stays the number of items actually returned");
    assertEquals(5L, page.getScannedCount(), "scannedCount comes from X-Apify-Pagination-Count");
  }

  @Test
  void listItemsScannedCountNullWhenHeaderAbsent() {
    MockTransport backend = MockTransport.ofConstant(200, "[{\"n\":1}]");
    PaginationList<JsonNode> page =
        client(backend).dataset("d1").listItems(new DatasetListItemsOptions()).join();
    assertEquals(null, page.getScannedCount(), "no header -> no scanned count reported");
  }

  @Test
  void advancesByScannedCountNotReturnedCount() {
    // Regression for the fully-filtered-page bug (see AsyncPaginatedPublisher.applyPage): a
    // server-side filter (clean/skipEmpty/skipHidden) can drop every item of a scanned page.
    // Advancing by the *returned* count (0) would stop iteration right there; advancing by the
    // *scanned* count (reported via X-Apify-Pagination-Count) must skip past the filtered-out
    // window and reach the real data on the next page.
    MockTransport backend =
        new MockTransport(
            List.of(
                // Page 1: scanned 2 rows, server-side filter dropped both.
                MockTransport.ok(200, "[]", Map.of("X-Apify-Pagination-Count", "2")),
                // Page 2: scanned 2 rows, 1 survived the filter.
                MockTransport.ok(200, "[{\"n\":3}]", Map.of("X-Apify-Pagination-Count", "2")),
                // Page 3: nothing left to scan - iteration ends.
                MockTransport.ok(200, "[]", Map.of("X-Apify-Pagination-Count", "0"))));
    List<JsonNode> seen =
        collect(
            client(backend)
                .dataset("d1")
                .iterateItems(new DatasetListItemsOptions().clean(true), 2L));
    List<Integer> ns = seen.stream().map(n -> n.get("n").asInt()).toList();
    assertEquals(List.of(3), ns, "the filtered-out page must not be skipped over or looped on");
    // 3 calls proves iteration did not stop after the fully-filtered first page: advancing (and
    // deciding exhaustion) by the *returned* count would read page 1 as "0 scanned" and stop right
    // there, silently losing item 3 on page 2.
    assertEquals(3, backend.calls);
  }

  @Test
  void advancesByScannedCountEvenWhenUnwindReturnsMoreThanScanned() {
    // Regression for the mirror-image bug the naive "trust the header only when it's >= the
    // returned count" guard introduced: `unwind` splits one scanned row's array field into several
    // output items, so returnedCount > scannedCount is a legitimate, common shape - not a sign the
    // header is unpopulated. A guard that falls back to the (larger) returned count here advances
    // the offset too far and silently drops the rows in between on the next page.
    MockTransport backend =
        new MockTransport(
            List.of(
                // Page 1: scanned 2 rows, each with a 2-element array that unwind splits in two.
                MockTransport.ok(
                    200,
                    "[{\"n\":1},{\"n\":2},{\"n\":3},{\"n\":4}]",
                    Map.of("X-Apify-Pagination-Count", "2")),
                // Page 2 (offset must be 2, the scanned count - not 4, the returned count): the
                // remaining 2 scanned rows, unwound into 4 items.
                MockTransport.ok(
                    200,
                    "[{\"n\":5},{\"n\":6},{\"n\":7},{\"n\":8}]",
                    Map.of("X-Apify-Pagination-Count", "2")),
                // Nothing left to scan.
                MockTransport.ok(200, "[]", Map.of("X-Apify-Pagination-Count", "0"))));
    List<JsonNode> seen =
        collect(
            client(backend)
                .dataset("d1")
                .iterateItems(new DatasetListItemsOptions().unwind(List.of("arr")), 2L));
    List<Integer> ns = seen.stream().map(n -> n.get("n").asInt()).toList();
    assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), ns, "no rows dropped between the unwound pages");
    assertEquals(3, backend.calls);
    // The decisive assertion: the second request's offset must be 2 (the scanned count), not 4
    // (the returned count) - MockTransport serves its scripted pages in call order regardless of
    // what offset is requested, so only inspecting the actual request URLs catches a guard that
    // picks the wrong (larger) value.
    assertTrue(backend.urls.get(1).contains("offset=2"), backend.urls.get(1));
    assertFalse(backend.urls.get(1).contains("offset=4"), backend.urls.get(1));
  }

  @Test
  void fallsBackToReturnedCountWhenHeaderMissing() {
    // Endpoints/mocks that do not send X-Apify-Pagination-Count (every collection but dataset
    // items, and this one deliberately omitting it) must keep behaving exactly as before: advance
    // by the number of items actually returned.
    MockTransport backend =
        new MockTransport(
            List.of(MockTransport.ok(200, "[{\"n\":1},{\"n\":2}]"), MockTransport.ok(200, "[]")));
    List<JsonNode> seen =
        collect(client(backend).dataset("d1").iterateItems(new DatasetListItemsOptions(), 2L));
    List<Integer> ns = seen.stream().map(n -> n.get("n").asInt()).toList();
    assertEquals(List.of(1, 2), ns);
    assertEquals(
        2, backend.calls, "falls back to items.length, same as before this header existed");
  }

  @Test
  void fallsBackToReturnedCountWhenHeaderIsImplausiblyLow() {
    // Regression for a real finding against the live API (see PaginationList.getScannedCount): it
    // was observed sending X-Apify-Pagination-Count: 0 on every page of an *unfiltered* listing,
    // even though each page genuinely scanned and returned real items - a combination that can
    // never
    // be a real "nothing scanned" answer (0 scanned rows can't produce items), so it must mean the
    // header is not populated for this request/endpoint yet. Trusting it anyway would advance the
    // offset by 0 and re-request (or, worse, treat the page as exhausted and stop).
    MockTransport backend =
        new MockTransport(
            List.of(
                MockTransport.ok(
                    200, "[{\"n\":1},{\"n\":2}]", Map.of("X-Apify-Pagination-Count", "0")),
                MockTransport.ok(200, "[{\"n\":3}]", Map.of("X-Apify-Pagination-Count", "0")),
                MockTransport.ok(200, "[]", Map.of("X-Apify-Pagination-Count", "0"))));
    List<JsonNode> seen =
        collect(client(backend).dataset("d1").iterateItems(new DatasetListItemsOptions(), 2L));
    List<Integer> ns = seen.stream().map(n -> n.get("n").asInt()).toList();
    assertEquals(List.of(1, 2, 3), ns, "must not stop early just because the header reads 0");
    assertEquals(3, backend.calls);
  }

  @Test
  void decodesIntoRequestedType() {
    MockTransport backend =
        new MockTransport(
            List.of(MockTransport.ok(200, "[{\"n\":7}]"), MockTransport.ok(200, "[]")));
    List<Item> seen =
        collect(
            client(backend)
                .dataset("d1")
                .iterateItems(new DatasetListItemsOptions(), 2L, Item.class));
    assertEquals(1, seen.size(), "typed iteration should decode and yield the item");
    assertEquals(7, seen.get(0).n());
  }
}
