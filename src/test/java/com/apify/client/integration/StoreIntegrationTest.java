package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.store.ActorStoreListItem;
import com.apify.client.store.StoreListOptions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StoreIntegrationTest extends IntegrationBase {

  @Test
  void listStore() {
    ApifyClient client = requireClient();
    var page = client.store().list(new StoreListOptions().limit(5L)).join();
    assertTrue(page.getItems().size() <= 5);
  }

  @Test
  void iterateStore() {
    ApifyClient client = requireClient();
    // Requests items one at a time (real backpressure) and stops once at least 12 have been seen,
    // without ever asking the publisher for more than that - unlike Publishers.collect(...), which
    // would drain the whole (large, shared) Store collection.
    AtomicInteger count = new AtomicInteger();
    Flow.Publisher<ActorStoreListItem> publisher =
        client.store().iterate(new StoreListOptions().limit(20L), 5L);
    CompletableFuture<Void> done = new CompletableFuture<>();
    publisher.subscribe(
        new Flow.Subscriber<>() {
          private Flow.Subscription subscription;

          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
          }

          @Override
          public void onNext(ActorStoreListItem item) {
            assertTrue(item.getId() != null && !item.getId().isEmpty());
            if (count.incrementAndGet() >= 12) {
              subscription.cancel();
              done.complete(null);
            } else {
              subscription.request(1);
            }
          }

          @Override
          public void onError(Throwable throwable) {
            done.completeExceptionally(throwable);
          }

          @Override
          public void onComplete() {
            done.complete(null);
          }
        });
    done.join();
    assertTrue(
        count.get() >= 12, "expected to iterate at least 12 store actors, got " + count.get());
  }
}
