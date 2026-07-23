package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.StorageListOptions;
import com.apify.client.keyvalue.GetRecordOptions;
import com.apify.client.keyvalue.KeyValueStore;
import com.apify.client.keyvalue.KeyValueStoreClient;
import com.apify.client.keyvalue.KeyValueStoreRecord;
import com.apify.client.keyvalue.ListKeysOptions;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KeyValueStoreIntegrationTest extends IntegrationBase {

  @Test
  void listKeyValueStores() {
    ApifyClient client = requireClient();
    assertTrue(
        client.keyValueStores().list(new StorageListOptions().limit(5L)).join().getTotal() >= 0);
  }

  @Test
  void getKeyValueStore() {
    ApifyClient client = requireClient();
    KeyValueStore store = client.keyValueStores().getOrCreate(uniqueName("kvs-get")).join();
    try {
      var got = client.keyValueStore(store.getId()).get().join();
      assertTrue(got.isPresent());
      assertEquals(store.getId(), got.get().getId());
    } finally {
      client.keyValueStore(store.getId()).delete().join();
    }
  }

  @Test
  void recordKeyWithSpecialChars() {
    ApifyClient client = requireClient();
    KeyValueStore store = client.keyValueStores().getOrCreate(uniqueName("kvs-special")).join();
    try {
      KeyValueStoreClient kvs = client.keyValueStore(store.getId());
      String key = "weird-key!'()";
      kvs.setRecordJson(key, Map.of("ok", true)).join();
      assertTrue(kvs.recordExists(key).join());
      Optional<KeyValueStoreRecord> rec = kvs.getRecord(key).join();
      assertTrue(rec.isPresent());
      kvs.deleteRecord(key).join();
    } finally {
      client.keyValueStore(store.getId()).delete().join();
    }
  }

  @Test
  void keyValueStoreCrudFlow() throws Exception {
    ApifyClient client = requireClient();
    KeyValueStore store = client.keyValueStores().getOrCreate(uniqueName("kvs-crud")).join();
    try {
      KeyValueStoreClient kvs = client.keyValueStore(store.getId());
      assertTrue(kvs.get().join().isPresent());
      kvs.setRecordJson("OUTPUT", Map.of("hello", "world")).join();
      assertTrue(kvs.recordExists("OUTPUT").join());
      Optional<KeyValueStoreRecord> rec = kvs.getRecord("OUTPUT").join();
      assertTrue(rec.isPresent());
      String value = new String(rec.get().getValue(), StandardCharsets.UTF_8);
      assertTrue(value.contains("world"), value);
      kvs.getRecord("OUTPUT", new GetRecordOptions().attachment(false)).join();
      var keys = kvs.listKeys(new ListKeysOptions()).join();
      assertTrue(!keys.getItems().isEmpty());
      kvs.update(Map.of("name", uniqueName("kvs-renamed"))).join();
      kvs.deleteRecord("OUTPUT").join();

      // list() step of the create/get/modify/list/delete flow: verify the just-created key-value
      // store appears in the top-level collection listing.
      boolean foundInList =
          pollUntil(
              LIST_FIND_ATTEMPTS,
              LIST_FIND_BACKOFF_MILLIS,
              () ->
                  client
                      .keyValueStores()
                      .list(new StorageListOptions().desc(true).limit(10L))
                      .join()
                      .getItems()
                      .stream()
                      .anyMatch(s -> store.getId().equals(s.getId())));
      assertTrue(
          foundInList, "expected the just-created key-value store to appear in the top-level list");
    } finally {
      client.keyValueStore(store.getId()).delete().join();
    }
  }

  @Test
  void recordPublicUrlIsFetchable() throws IOException, InterruptedException {
    ApifyClient client = requireClient();
    KeyValueStore store = client.keyValueStores().getOrCreate(uniqueName("kvs-pub")).join();
    try {
      KeyValueStoreClient kvs = client.keyValueStore(store.getId());
      kvs.setRecordJson("OUTPUT", Map.of("pub", true)).join();
      String url = kvs.getRecordPublicUrl("OUTPUT").join();
      assertTrue(url != null && !url.isEmpty());

      HttpResponse<byte[]> resp =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(URI.create(url)).build(),
                  HttpResponse.BodyHandlers.ofByteArray());
      assertTrue(
          resp.statusCode() < 300,
          "expected success fetching public url, got " + resp.statusCode());
    } finally {
      client.keyValueStore(store.getId()).delete().join();
    }
  }
}
