package com.apify.client.internal;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A backpressure-aware {@link Flow.Publisher} over an already-in-flight, single-fetch {@link List}
 * - for the handful of "collections" (Actor version env-vars, Actor versions) that are not actually
 * offset/limit-paginated on the server: it always returns every item in one response, so routing it
 * through {@link AsyncPaginatedPublisher}'s repeat-until-empty-page paging engine would fetch (and
 * re-emit) the same full list forever. This fetches once, then drains the resulting list to the
 * subscriber according to its signalled demand.
 *
 * <p>Supports a single subscriber, like {@link AsyncPaginatedPublisher}.
 */
public final class ListPublisher<T> implements Flow.Publisher<T> {

  private final CompletableFuture<List<T>> source;
  private final AtomicBoolean subscribed = new AtomicBoolean();

  public ListPublisher(CompletableFuture<List<T>> source) {
    this.source = source;
  }

  @Override
  public void subscribe(Flow.Subscriber<? super T> subscriber) {
    if (!subscribed.compareAndSet(false, true)) {
      subscriber.onSubscribe(NoopSubscription.INSTANCE);
      subscriber.onError(
          new IllegalStateException(
              "This publisher supports only a single subscriber (already subscribed)"));
      return;
    }
    subscriber.onSubscribe(new Session(subscriber));
  }

  private enum NoopSubscription implements Flow.Subscription {
    INSTANCE;

    @Override
    public void request(long n) {}

    @Override
    public void cancel() {}
  }

  private final class Session implements Flow.Subscription {
    private final Flow.Subscriber<? super T> subscriber;
    private final AtomicLong requested = new AtomicLong();
    private final AtomicBoolean draining = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean terminated = new AtomicBoolean();

    // Only touched from within the draining-guarded section; see AsyncPaginatedPublisher.Session.
    private List<T> items;
    private int pos;

    Session(Flow.Subscriber<? super T> subscriber) {
      this.subscriber = subscriber;
    }

    @Override
    public void request(long n) {
      if (n <= 0) {
        if (terminated.compareAndSet(false, true)) {
          subscriber.onError(
              new IllegalArgumentException(
                  "Reactive Streams violation: requested amount must be positive, was " + n));
        }
        return;
      }
      requested.updateAndGet(current -> addSaturating(current, n));
      drain();
    }

    @Override
    public void cancel() {
      cancelled.set(true);
    }

    private void drain() {
      if (draining.compareAndSet(false, true)) {
        if (items == null) {
          source.whenComplete(
              (list, error) -> {
                if (cancelled.get()) {
                  draining.set(false);
                  return;
                }
                if (error != null) {
                  terminate(() -> subscriber.onError(HttpClientCore.unwrapCompletion(error)));
                  return;
                }
                items = list;
                drainLoop();
              });
        } else {
          drainLoop();
        }
      }
    }

    private void drainLoop() {
      while (!cancelled.get() && requested.get() > 0 && pos < items.size()) {
        T item = items.get(pos++);
        requested.decrementAndGet();
        subscriber.onNext(item);
      }
      if (cancelled.get()) {
        draining.set(false);
        return;
      }
      if (pos >= items.size()) {
        terminate(subscriber::onComplete);
        return;
      }
      draining.set(false);
      if (requested.get() > 0 && pos < items.size()) {
        drain();
      }
    }

    private void terminate(Runnable callback) {
      if (terminated.compareAndSet(false, true)) {
        callback.run();
      }
      draining.set(false);
    }
  }

  private static long addSaturating(long a, long b) {
    long sum = a + b;
    return sum < 0 ? Long.MAX_VALUE : sum;
  }
}
