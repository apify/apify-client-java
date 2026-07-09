package com.apify.client.examples;

import com.apify.client.ApifyClient;
import com.apify.client.StoreListOptions;

/**
 * Lazily iterates over Actors in the Apify Store using the convenience iteration method, printing
 * the first few.
 */
public final class IterateStore {
  private IterateStore() {}

  public static void main(String[] args) {
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));

    int[] index = {0};
    client
        .store()
        .iterate(new StoreListOptions().limit(10L))
        .limit(5)
        .forEach(
            item ->
                System.out.println(
                    (++index[0]) + ". " + item.getUsername() + "/" + item.getName()));
  }
}
