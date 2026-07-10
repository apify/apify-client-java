package com.apify.client.examples;

import com.apify.client.ActorRun;
import com.apify.client.ActorStartOptions;
import com.apify.client.ApifyClient;
import com.apify.client.DatasetListItemsOptions;
import java.util.Optional;

/**
 * Starts an Actor run, waits for it to finish, then fetches the Actor's last run and reads its
 * default storages.
 */
public final class RunAndLastRunStorages {
  private RunAndLastRunStorages() {}

  public static void main(String[] args) {
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));

    client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L);

    Optional<ActorRun> lastRun = client.actor("apify/hello-world").lastRun("SUCCEEDED").get();
    if (lastRun.isEmpty()) {
      throw new IllegalStateException("no succeeded run found");
    }
    ActorRun run = lastRun.get();
    System.out.println("Last run: " + run.getId());

    var items = client.dataset(run.getDefaultDatasetId()).listItems(new DatasetListItemsOptions());
    System.out.println("Items in this page of the last run's dataset: " + items.getCount());
    client.keyValueStore(run.getDefaultKeyValueStoreId()).getRecord("OUTPUT");
  }
}
