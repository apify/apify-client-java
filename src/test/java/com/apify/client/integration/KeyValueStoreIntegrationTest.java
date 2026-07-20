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
    assertTrue(client.keyValueStores().list(new StorageListOptions().limit(5L)).getTotal() >= 0);
  }

  @Test
  void getKeyValueStore() {
    ApifyClient client = requireClient();
    KeyValueStore store = client.keyValueStores().getOrCreate(uniqueName("kvs-get"));
    try {
      var got = client.keyValueStore(store.getId()).get();
      assertTrue(got.isPresent());
      assertEquals(store.getId(), got.get().getId());
    } finally {
      client.keyValueStore(store.getId()).delete();
    }
  }

  @Test
  void recordKeyWithSpecialChars() {
    ApifyClient client = requireClient();
    KeyValueStore store = client.keyValueStores().getOrCreate(uniqueName("kvs-special"));
    try {
      KeyValueStoreClient kvs = client.keyValueStore(store.getId());
      String key = "weird-key!'()";
      kvs.setRecordJson(key, Map.of("ok", true));
      assertTrue(kvs.recordExists(key));
      Optional<KeyValueStoreRecord> rec = kvs.getRecord(key);
      assertTrue(rec.isPresent());
      kvs.deleteRecord(key);
    } finally {
      client.keyValueStore(store.getId()).delete();
    }
  }

  @Test
  void keyValueStoreCrudFlow() throws Exception {
    ApifyClient client = requireClient();
    KeyValueStore store = client.keyValueStores().getOrCreate(uniqueName("kvs-crud"));
    try {
      KeyValueStoreClient kvs = client.keyValueStore(store.getId());
      assertTrue(kvs.get().isPresent());
      kvs.setRecordJson("OUTPUT", Map.of("hello", "world"));
      assertTrue(kvs.recordExists("OUTPUT"));
      Optional<KeyValueStoreRecord> rec = kvs.getRecord("OUTPUT");
      assertTrue(rec.isPresent());
      String value = new String(rec.get().getValue(), StandardCharsets.UTF_8);
      assertTrue(value.contains("world"), value);
      kvs.getRecord("OUTPUT", new GetRecordOptions().attachment(false));
      var keys = kvs.listKeys(new ListKeysOptions());
      assertTrue(!keys.getItems().isEmpty());
      kvs.update(Map.of("name", uniqueName("kvs-renamed")));
      kvs.deleteRecord("OUTPUT");
    } finally {
      client.keyValueStore(store.getId()).delete();
    }
  }

  @Test
  void recordPublicUrlIsFetchable() throws IOException, InterruptedException {
    ApifyClient client = requireClient();
    KeyValueStore store = client.keyValueStores().getOrCreate(uniqueName("kvs-pub"));
    try {
      KeyValueStoreClient kvs = client.keyValueStore(store.getId());
      kvs.setRecordJson("OUTPUT", Map.of("pub", true));
      String url = kvs.getRecordPublicUrl("OUTPUT");
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
      client.keyValueStore(store.getId()).delete();
    }
  }
}
