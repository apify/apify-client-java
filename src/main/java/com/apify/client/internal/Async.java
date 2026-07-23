package com.apify.client.internal;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Shared scheduling primitive for the client's non-blocking retry backoff and wait-for-finish
 * polling. Internal to the client.
 *
 * <p>A single daemon-thread {@link ScheduledExecutorService} backs every delay: this is a client
 * that never blocks a caller's thread waiting on the network, so a retry/poll delay is expressed as
 * a scheduled {@link CompletableFuture} completion rather than {@code Thread.sleep}. The scheduler
 * thread only ever fires a timer (sub-millisecond of work); the actual HTTP call that follows each
 * delay runs on the JDK {@link java.net.http.HttpClient}'s own async executor, so this single
 * thread is never a throughput bottleneck.
 */
public final class Async {

  private static final ThreadFactory DAEMON_THREAD_FACTORY =
      runnable -> {
        Thread t = new Thread(runnable, "apify-client-scheduler");
        t.setDaemon(true);
        return t;
      };

  private static final ScheduledExecutorService SCHEDULER =
      Executors.newSingleThreadScheduledExecutor(DAEMON_THREAD_FACTORY);

  private Async() {}

  /** Returns a future that completes after {@code delay} has elapsed (never negative/blocking). */
  public static CompletableFuture<Void> delay(Duration delay) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    long millis = Math.max(0, delay.toMillis());
    SCHEDULER.schedule(() -> future.complete(null), millis, TimeUnit.MILLISECONDS);
    return future;
  }
}
