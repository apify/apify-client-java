package com.apify.client.internal;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * A {@link CompletableFuture} whose cancellation reaches the work behind it. Cancelling a plain
 * {@code CompletableFuture} completes only that future: the request it waits for, the retry or poll
 * chain that produced it and the timers that drive it keep running. Every future the client returns
 * is a {@code CancellableFuture}, and so is every stage derived from it with {@code thenApply},
 * {@code thenCompose}, {@code handle}, ...: cancelling the future a caller holds cancels each
 * upstream stage (and the future {@code thenCompose} is waiting on) down to the HTTP request in
 * flight, and stops the retries, polls and pages that would follow. Internal to the client.
 */
public final class CancellableFuture<T> extends CompletableFuture<T> {

  /**
   * The futures this one depends on, cancelled together with it. Held weakly: a future that is
   * still pending is kept alive by whatever will complete it (the request in flight, the timer, the
   * stage it feeds), and holding a completed one strongly would keep its result (a response body,
   * say) reachable from every later stage for as long as the call lives, which for a long {@code
   * waitForFinish} poll loop is unbounded.
   */
  private final ConcurrentLinkedQueue<WeakReference<CompletableFuture<?>>> upstream =
      new ConcurrentLinkedQueue<>();

  public CancellableFuture() {}

  /**
   * A future that completes as {@code source} does and, when cancelled, cancels {@code source}.
   * Wraps the future a transport returns, so cancelling a call aborts the request in flight.
   */
  public static <T> CancellableFuture<T> wrap(CompletionStage<T> source) {
    CancellableFuture<T> result = new CancellableFuture<>();
    CompletableFuture<T> sourceFuture = source.toCompletableFuture();
    result.link(sourceFuture);
    sourceFuture.whenComplete(
        (value, error) -> {
          if (error == null) {
            result.complete(value);
          } else {
            result.completeExceptionally(error);
          }
        });
    return result;
  }

  /** An already completed future. */
  public static <T> CancellableFuture<T> completed(T value) {
    CancellableFuture<T> future = new CancellableFuture<>();
    future.complete(value);
    return future;
  }

  /** An already failed future. */
  public static <T> CancellableFuture<T> failed(Throwable error) {
    CancellableFuture<T> future = new CancellableFuture<>();
    future.completeExceptionally(error);
    return future;
  }

  /** A future that completes once all of {@code futures} have, and cancels them when cancelled. */
  public static CancellableFuture<Void> allOf(List<? extends CompletableFuture<?>> futures) {
    CancellableFuture<Void> result = new CancellableFuture<>();
    futures.forEach(result::link);
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
        .whenComplete(
            (unused, error) -> {
              if (error == null) {
                result.complete(null);
              } else {
                result.completeExceptionally(error);
              }
            });
    return result;
  }

  /** Makes {@code other} be cancelled whenever this future is (now, if it already is). */
  public void link(CompletableFuture<?> other) {
    upstream.add(new WeakReference<>(other));
    if (isCancelled()) {
      other.cancel(true);
    }
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean cancelled = super.cancel(mayInterruptIfRunning);
    if (cancelled) {
      for (WeakReference<CompletableFuture<?>> reference : upstream) {
        CompletableFuture<?> source = reference.get();
        if (source != null) {
          source.cancel(mayInterruptIfRunning);
        }
      }
    }
    return cancelled;
  }

  /**
   * A defensive copy: completes with this future's outcome, but cancelling the copy does not cancel
   * this future or its request.
   */
  @Override
  public CompletableFuture<T> copy() {
    CancellableFuture<T> copy = new CancellableFuture<>();
    whenComplete(
        (value, error) -> {
          if (error == null) {
            copy.complete(value);
          } else {
            copy.completeExceptionally(
                error instanceof CompletionException ? error.getCause() : error);
          }
        });
    return copy;
  }

  /** Every stage derived from this one cancels this one when it is cancelled. */
  @Override
  public <U> CompletableFuture<U> newIncompleteFuture() {
    CancellableFuture<U> dependent = new CancellableFuture<>();
    dependent.upstream.add(new WeakReference<>(this));
    return dependent;
  }

  /**
   * As {@link CompletableFuture#thenCompose}, additionally linking the future {@code fn} returns,
   * so cancelling the composed stage cancels the work it was waiting on, and skipping {@code fn}
   * altogether once the composed stage is cancelled (so a cancelled call sends no further request).
   */
  @Override
  public <U> CompletableFuture<U> thenCompose(
      Function<? super T, ? extends CompletionStage<U>> fn) {
    Composition<U> composition = new Composition<>();
    CompletableFuture<U> composed =
        super.thenCompose(
            value -> {
              CancellableFuture<U> dependent = composition.dependent;
              if (dependent != null && dependent.isCancelled()) {
                return failed(new CancellationException());
              }
              CompletionStage<U> inner = fn.apply(value);
              CompletableFuture<U> innerFuture = inner.toCompletableFuture();
              synchronized (composition) {
                dependent = composition.dependent;
                if (dependent == null) {
                  composition.inner = innerFuture;
                }
              }
              if (dependent != null) {
                dependent.link(innerFuture);
              }
              return inner;
            });
    CancellableFuture<U> dependent = (CancellableFuture<U>) composed;
    CompletableFuture<U> early;
    synchronized (composition) {
      composition.dependent = dependent;
      early = composition.inner;
      composition.inner = null;
    }
    if (early != null) {
      dependent.link(early);
    }
    return composed;
  }

  /**
   * Hands the composed stage and the future {@code fn} returned to each other, whichever is first.
   */
  private static final class Composition<U> {
    volatile CancellableFuture<U> dependent;
    CompletableFuture<U> inner;
  }
}
