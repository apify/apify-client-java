package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ActorStoreListItem;
import com.apify.client.ApifyClient;
import com.apify.client.StoreListOptions;
import java.util.List;
import java.util.stream.Collectors;
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
    List<ActorStoreListItem> items =
        client
            .store()
            .iterate(new StoreListOptions().limit(5L))
            .limit(12)
            .collect(Collectors.toList());
    for (ActorStoreListItem item : items) {
      assertTrue(item.getId() != null && !item.getId().isEmpty());
    }
    assertTrue(
        items.size() >= 12, "expected to iterate at least 12 store actors, got " + items.size());
  }
}
