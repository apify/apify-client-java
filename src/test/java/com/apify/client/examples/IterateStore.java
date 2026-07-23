package com.apify.client.examples;

import com.apify.client.ApifyClient;
import com.apify.client.store.ActorStoreListItem;
import com.apify.client.store.StoreListOptions;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;

/**
 * Lazily iterates over Actors in the Apify Store using the convenience iteration method, printing
 * the first few without fetching (or even requesting) the rest of the collection.
 */
public final class IterateStore {
  private IterateStore() {}

  public static void main(String[] args) throws InterruptedException {
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));

    CountDownLatch done = new CountDownLatch(1);
    client
        .store()
        .iterate(new StoreListOptions().limit(10L), 10L)
        .subscribe(
            new Flow.Subscriber<>() {
              private Flow.Subscription subscription;
              private int count;

              @Override
              public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1); // pull one item at a time
              }

              @Override
              public void onNext(ActorStoreListItem item) {
                count++;
                System.out.println(count + ". " + item.getUsername() + "/" + item.getName());
                if (count >= 5) {
                  subscription.cancel(); // stop early; no further pages are fetched
                  done.countDown();
                } else {
                  subscription.request(1);
                }
              }

              @Override
              public void onError(Throwable throwable) {
                done.countDown();
                throw new RuntimeException(throwable);
              }

              @Override
              public void onComplete() {
                done.countDown();
              }
            });
    done.await();
  }
}
