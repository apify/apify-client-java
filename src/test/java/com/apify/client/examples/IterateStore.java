package com.apify.client.examples;

import com.apify.client.ApifyClient;
import com.apify.client.store.ActorStoreListItem;
import com.apify.client.store.StoreListOptions;
import java.util.Iterator;

/**
 * Lazily iterates over Actors in the Apify Store using the convenience iteration method, printing
 * the first few.
 */
public final class IterateStore {
  private IterateStore() {}

  public static void main(String[] args) {
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));

    Iterator<ActorStoreListItem> it =
        client.store().iterate(new StoreListOptions().limit(10L), 10L);
    int count = 0;
    while (count < 5 && it.hasNext()) {
      ActorStoreListItem item = it.next();
      System.out.println((count + 1) + ". " + item.getUsername() + "/" + item.getName());
      count++;
    }
  }
}
