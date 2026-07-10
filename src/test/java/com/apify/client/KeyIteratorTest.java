package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hermetic (token-free) tests for the cursor-based key iterator ({@link
 * KeyValueStoreClient#iterateKeys}), driven by a {@link MockBackend}: cursor chaining across pages,
 * the {@code limit} total-cap, and empty-page / empty-cursor termination.
 */
class KeyIteratorTest {

  private static ApifyClient client(MockBackend backend) {
    return ApifyClient.builder()
        .token("test-token")
        .httpBackend(backend)
        .maxRetries(0)
        .minDelayBetweenRetries(Duration.ofMillis(1))
        .build();
  }

  private static List<String> keys(Iterator<KeyValueStoreKey> it) {
    List<String> out = new ArrayList<>();
    while (it.hasNext()) {
      out.add(it.next().getKey());
    }
    return out;
  }

  @Test
  void chainsAcrossPagesUsingCursor() {
    MockBackend backend =
        new MockBackend(
            List.of(
                MockBackend.ok(
                    200,
                    "{\"data\":{\"items\":[{\"key\":\"a\",\"size\":1},{\"key\":\"b\",\"size\":1}],"
                        + "\"nextExclusiveStartKey\":\"b\",\"isTruncated\":true}}"),
                MockBackend.ok(
                    200,
                    "{\"data\":{\"items\":[{\"key\":\"c\",\"size\":1}],"
                        + "\"nextExclusiveStartKey\":null,\"isTruncated\":false}}")));
    List<String> got = keys(client(backend).keyValueStore("s").iterateKeys(new ListKeysOptions()));
    assertEquals(List.of("a", "b", "c"), got, "iterator should chain pages via the cursor");
    assertEquals(2, backend.calls, "stops once the next cursor is null");
    assertTrue(backend.lastUrl.contains("exclusiveStartKey=b"), backend.lastUrl);
  }

  @Test
  void limitCapsTotalKeysYielded() {
    MockBackend backend =
        MockBackend.ofConstant(
            200,
            "{\"data\":{\"items\":[{\"key\":\"a\",\"size\":1},{\"key\":\"b\",\"size\":1},"
                + "{\"key\":\"c\",\"size\":1}],\"nextExclusiveStartKey\":\"c\",\"isTruncated\":true}}");
    List<String> got =
        keys(client(backend).keyValueStore("s").iterateKeys(new ListKeysOptions().limit(2L)));
    assertEquals(List.of("a", "b"), got, "limit caps the total keys yielded");
    assertEquals(1, backend.calls, "the cap is reached within the first page");
    assertTrue(backend.lastUrl.contains("limit=2"), backend.lastUrl);
  }

  @Test
  void stopsOnEmptyPage() {
    MockBackend backend =
        MockBackend.ofConstant(
            200, "{\"data\":{\"items\":[],\"nextExclusiveStartKey\":\"z\",\"isTruncated\":true}}");
    Iterator<KeyValueStoreKey> it =
        client(backend).keyValueStore("s").iterateKeys(new ListKeysOptions());
    assertFalse(it.hasNext(), "an empty page ends iteration even if a next cursor is reported");
    assertEquals(1, backend.calls);
  }
}
