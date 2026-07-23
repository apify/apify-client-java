package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.keyvalue.KeyValueStoreClient;
import com.apify.client.keyvalue.KeyValueStoreKey;
import com.apify.client.keyvalue.ListKeysOptions;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hermetic (token-free) tests for the cursor-based key iterator ({@link
 * KeyValueStoreClient#iterateKeys}), driven by a {@link MockTransport}: cursor chaining across
 * pages, the {@code limit} total-cap, and empty-page / empty-cursor termination.
 */
class KeyIteratorTest {

  private static ApifyClient client(MockTransport backend) {
    return ApifyClient.builder()
        .token("test-token")
        .httpTransport(backend)
        .maxRetries(0)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  private static List<String> keys(KeyValueStoreClient store, ListKeysOptions options) {
    return Publishers.collect(store.iterateKeys(options)).join().stream()
        .map(KeyValueStoreKey::getKey)
        .toList();
  }

  private static List<String> keys(
      KeyValueStoreClient store, ListKeysOptions options, Long chunkSize) {
    return Publishers.collect(store.iterateKeys(options, chunkSize)).join().stream()
        .map(KeyValueStoreKey::getKey)
        .toList();
  }

  @Test
  void chainsAcrossPagesUsingCursor() {
    MockTransport backend =
        new MockTransport(
            List.of(
                MockTransport.ok(
                    200,
                    "{\"data\":{\"items\":[{\"key\":\"a\",\"size\":1},{\"key\":\"b\",\"size\":1}],"
                        + "\"nextExclusiveStartKey\":\"b\",\"isTruncated\":true}}"),
                MockTransport.ok(
                    200,
                    "{\"data\":{\"items\":[{\"key\":\"c\",\"size\":1}],"
                        + "\"nextExclusiveStartKey\":null,\"isTruncated\":false}}")));
    List<String> got = keys(client(backend).keyValueStore("s"), new ListKeysOptions());
    assertEquals(List.of("a", "b", "c"), got, "iterator should chain pages via the cursor");
    assertEquals(2, backend.calls, "stops once the next cursor is null");
    assertTrue(backend.lastUrl.contains("exclusiveStartKey=b"), backend.lastUrl);
  }

  @Test
  void limitCapsTotalKeysYielded() {
    MockTransport backend =
        MockTransport.ofConstant(
            200,
            "{\"data\":{\"items\":[{\"key\":\"a\",\"size\":1},{\"key\":\"b\",\"size\":1},"
                + "{\"key\":\"c\",\"size\":1}],\"nextExclusiveStartKey\":\"c\",\"isTruncated\":true}}");
    List<String> got = keys(client(backend).keyValueStore("s"), new ListKeysOptions().limit(2L));
    assertEquals(List.of("a", "b"), got, "limit caps the total keys yielded");
    assertEquals(1, backend.calls, "the cap is reached within the first page");
    assertTrue(backend.lastUrl.contains("limit=2"), backend.lastUrl);
  }

  @Test
  void chunkSizeSetsPerRequestPageSize() {
    MockTransport backend =
        new MockTransport(
            List.of(
                MockTransport.ok(
                    200,
                    "{\"data\":{\"items\":[{\"key\":\"a\",\"size\":1},{\"key\":\"b\",\"size\":1}],"
                        + "\"nextExclusiveStartKey\":\"b\",\"isTruncated\":true}}"),
                MockTransport.ok(
                    200,
                    "{\"data\":{\"items\":[{\"key\":\"c\",\"size\":1},{\"key\":\"d\",\"size\":1}],"
                        + "\"nextExclusiveStartKey\":\"d\",\"isTruncated\":true}}"),
                MockTransport.ok(
                    200,
                    "{\"data\":{\"items\":[{\"key\":\"e\",\"size\":1}],"
                        + "\"nextExclusiveStartKey\":null,\"isTruncated\":false}}")));
    List<String> got = keys(client(backend).keyValueStore("s"), new ListKeysOptions(), 2L);
    assertEquals(List.of("a", "b", "c", "d", "e"), got, "chunkSize pages the full collection");
    assertEquals(3, backend.calls);
    assertTrue(backend.lastUrl.contains("limit=2"), "chunkSize is sent as the page limit");
  }

  @Test
  void chunkSizeAndLimitCombine() {
    // limit=3 (total cap) with chunkSize=2: first page requests 2, second requests min(1,2)=1.
    MockTransport backend =
        new MockTransport(
            List.of(
                MockTransport.ok(
                    200,
                    "{\"data\":{\"items\":[{\"key\":\"a\",\"size\":1},{\"key\":\"b\",\"size\":1}],"
                        + "\"nextExclusiveStartKey\":\"b\",\"isTruncated\":true}}"),
                MockTransport.ok(
                    200,
                    "{\"data\":{\"items\":[{\"key\":\"c\",\"size\":1},{\"key\":\"d\",\"size\":1}],"
                        + "\"nextExclusiveStartKey\":\"d\",\"isTruncated\":true}}")));
    List<String> got =
        keys(client(backend).keyValueStore("s"), new ListKeysOptions().limit(3L), 2L);
    assertEquals(List.of("a", "b", "c"), got, "the total cap trims across chunked pages");
    assertEquals(2, backend.calls);
    assertTrue(
        backend.lastUrl.contains("limit=1"), "the last page requests only the remaining cap");
  }

  @Test
  void stopsOnEmptyPage() {
    MockTransport backend =
        MockTransport.ofConstant(
            200, "{\"data\":{\"items\":[],\"nextExclusiveStartKey\":\"z\",\"isTruncated\":true}}");
    List<String> got = keys(client(backend).keyValueStore("s"), new ListKeysOptions());
    assertTrue(got.isEmpty(), "an empty page ends iteration even if a next cursor is reported");
    assertEquals(1, backend.calls);
  }
}
