package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ActorStoreListItem;
import com.apify.client.ApifyClient;
import com.apify.client.StoreListOptions;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

class StoreIntegrationTest extends IntegrationBase {

  @Test
  void listStore() {
    ApifyClient client = requireClient();
    var page = client.store().list(new StoreListOptions().limit(5L));
    assertTrue(page.getItems().size() <= 5);
  }

  @Test
  void iterateStore() {
    ApifyClient client = requireClient();
    Iterator<ActorStoreListItem> it = client.store().iterate(new StoreListOptions().limit(20L), 5L);
    int count = 0;
    while (count < 12 && it.hasNext()) {
      ActorStoreListItem item = it.next();
      assertTrue(item.getId() != null && !item.getId().isEmpty());
      count++;
    }
    assertTrue(count >= 12, "expected to iterate at least 12 store actors, got " + count);
  }
}
