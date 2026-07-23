package com.apify.client.examples;

import com.apify.client.ApifyClient;
import com.apify.client.actor.ActorStartOptions;
import com.apify.client.dataset.DatasetListItemsOptions;
import com.apify.client.run.ActorRun;

/**
 * Runs an existing store Actor ({@code apify/hello-world}), waits for it to finish, and reads its
 * default dataset.
 */
public final class RunStoreActor {
  private RunStoreActor() {}

  public static void main(String[] args) {
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));

    ActorRun run = client.actor("apify/hello-world").call(null, new ActorStartOptions(), 120L);
    System.out.println("Run " + run.getId() + " finished with status " + run.getStatus());

    var items = client.dataset(run.getDefaultDatasetId()).listItems(new DatasetListItemsOptions());
    System.out.println("Items in this page of the default dataset: " + items.getCount());
  }
}
