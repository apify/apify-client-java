package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.PaginationList;
import com.apify.client.StorageListOptions;
import com.apify.client.dataset.Dataset;
import com.apify.client.dataset.DatasetClient;
import com.apify.client.dataset.DatasetDownloadOptions;
import com.apify.client.dataset.DatasetListItemsOptions;
import com.apify.client.dataset.DownloadItemsFormat;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class DatasetIntegrationTest extends IntegrationBase {

  @Test
  void listDatasets() {
    ApifyClient client = requireClient();
    assertTrue(client.datasets().list(new StorageListOptions().limit(5L)).join().getTotal() >= 0);
  }

  @Test
  void getDataset() {
    ApifyClient client = requireClient();
    Dataset ds = client.datasets().getOrCreate(uniqueName("ds-get")).join();
    try {
      var got = client.dataset(ds.getId()).get().join();
      assertTrue(got.isPresent());
      assertEquals(ds.getId(), got.get().getId());
    } finally {
      client.dataset(ds.getId()).delete().join();
    }
  }

  @Test
  void datasetCrudFlow() {
    ApifyClient client = requireClient();
    Dataset ds = client.datasets().getOrCreate(uniqueName("ds-crud")).join();
    try {
      DatasetClient dataset = client.dataset(ds.getId());
      assertTrue(dataset.get().join().isPresent());

      dataset
          .pushItems(
              List.of(
                  Map.of("url", "https://a.com", "n", 1),
                  Map.of("url", "https://b.com", "n", 2),
                  Map.of("url", "https://c.com", "n", 3)))
          .join();

      PaginationList<JsonNode> page = dataset.listItems(new DatasetListItemsOptions()).join();
      assertEquals(3, page.getCount());
      assertEquals(3, page.getItems().size());
      assertEquals(1, page.getItems().get(0).get("n").asInt());

      byte[] csv =
          dataset
              .downloadItems(DownloadItemsFormat.CSV, new DatasetDownloadOptions().bom(true))
              .join();
      assertTrue(new String(csv, StandardCharsets.UTF_8).contains("url"));

      String url = dataset.createItemsPublicUrl(new DatasetListItemsOptions(), null).join();
      assertTrue(url != null && !url.isEmpty());

      dataset.getStatistics().join();

      Dataset updated = dataset.update(Map.of("name", uniqueName("ds-renamed"))).join();
      assertTrue(updated.getName() != null && !updated.getName().isEmpty());

      // list() step of the create/get/modify/list/delete flow: verify the just-created dataset
      // appears in the top-level collection listing.
      boolean foundInList =
          pollUntil(
              LIST_FIND_ATTEMPTS,
              LIST_FIND_BACKOFF_MILLIS,
              () ->
                  client
                      .datasets()
                      .list(new StorageListOptions().desc(true).limit(10L))
                      .join()
                      .getItems()
                      .stream()
                      .anyMatch(d -> ds.getId().equals(d.getId())));
      assertTrue(foundInList, "expected the just-created dataset to appear in the top-level list");
    } finally {
      client.dataset(ds.getId()).delete().join();
    }
  }
}
