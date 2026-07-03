package com.apify.client.examples;

import com.apify.client.ApifyClient;
import com.apify.client.Dataset;
import com.apify.client.DatasetListItemsOptions;
import com.apify.client.KeyValueStore;
import com.apify.client.KeyValueStoreRecord;
import com.apify.client.RequestQueue;
import com.apify.client.RequestQueueRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Demonstrates each storage type: create the storage, push data to it, then read the data back.
 *
 * <p>Run by executing {@link #main(String[])} with {@code APIFY_TOKEN} set.
 */
public final class Storages {
  private Storages() {}

  public static void main(String[] args) {
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));
    String suffix = Long.toString(System.currentTimeMillis());

    // Dataset: create, push items, read them back.
    Dataset dataset = client.datasets().getOrCreate("java-example-ds-" + suffix);
    try {
      client.dataset(dataset.getId()).pushItems(List.of(Map.of("hello", "world")));
      var items = client.dataset(dataset.getId()).listItems(new DatasetListItemsOptions());
      System.out.println("Dataset items: " + items.getItems());
    } finally {
      client.dataset(dataset.getId()).delete();
    }

    // Key-value store: create, set a record, read it back.
    KeyValueStore store = client.keyValueStores().getOrCreate("java-example-kvs-" + suffix);
    try {
      client.keyValueStore(store.getId()).setRecordJson("OUTPUT", Map.of("answer", 42));
      Optional<KeyValueStoreRecord> record =
          client.keyValueStore(store.getId()).getRecord("OUTPUT");
      record.ifPresent(r -> System.out.println("KVS record bytes: " + r.getValue().length));
    } finally {
      client.keyValueStore(store.getId()).delete();
    }

    // Request queue: create, add a request, read the head.
    RequestQueue queue = client.requestQueues().getOrCreate("java-example-rq-" + suffix);
    try {
      client
          .requestQueue(queue.getId())
          .addRequest(new RequestQueueRequest("https://example.com", "example"), false);
      var head = client.requestQueue(queue.getId()).listHead(10L);
      System.out.println("Request queue head size: " + head.getItems().size());
    } finally {
      client.requestQueue(queue.getId()).delete();
    }
  }
}
