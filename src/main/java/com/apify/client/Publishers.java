package com.apify.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A small bridge from this client's async {@link Flow.Publisher}-based iteration helpers to a plain
 * {@link List}, for callers who want "give me everything" rather than driving a {@link
 * Flow.Subscriber} themselves.
 *
 * <p>Every {@code iterate(...)} method across the resource clients (e.g. {@code
 * ActorCollectionClient#iterate}) returns a {@link Flow.Publisher} - the JDK's built-in Reactive
 * Streams type - so a large paginated collection can be followed without blocking a thread on each
 * page's network round-trip, with the subscriber controlling how many items are in flight at once
 * via {@link Flow.Subscription#request(long)}. {@link #collect(Flow.Publisher)} is a convenience
 * subscriber that requests everything unbounded and collects it into a {@link CompletableFuture} of
 * a {@link List}, for the common case where backpressure is not a concern (e.g. because the caller
 * knows the total is bounded, or a test simply wants the full list to assert on).
 */
public final class Publishers {

  private Publishers() {}

  /**
   * Subscribes to {@code publisher} with unbounded demand and collects every emitted item into a
   * {@link List}, in emission order. The returned future completes with the full list once the
   * publisher signals completion, or exceptionally with whatever error the publisher signalled.
   */
  public static <T> CompletableFuture<List<T>> collect(Flow.Publisher<T> publisher) {
    CompletableFuture<List<T>> result = new CompletableFuture<>();
    ConcurrentLinkedQueue<T> items = new ConcurrentLinkedQueue<>();
    AtomicBoolean done = new AtomicBoolean();
    publisher.subscribe(
        new Flow.Subscriber<>() {
          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(T item) {
            items.add(item);
          }

          @Override
          public void onError(Throwable throwable) {
            if (done.compareAndSet(false, true)) {
              result.completeExceptionally(throwable);
            }
          }

          @Override
          public void onComplete() {
            if (done.compareAndSet(false, true)) {
              result.complete(List.copyOf(items));
            }
          }
        });
    return result;
  }
}
