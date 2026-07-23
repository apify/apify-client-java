package com.apify.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Test-only helper for consuming this now-async client's {@link CompletableFuture}-returning
 * methods synchronously in JUnit assertions.
 *
 * <p>{@link CompletableFuture#join()} wraps any exceptional completion in a {@link
 * CompletionException}, which would otherwise defeat {@code assertThrows(ApifyApiException.class,
 * ...)}-style assertions throughout this suite (they expect the client's own exception type, not a
 * wrapper). {@link #await(CompletableFuture)} unwraps that one layer so the original exception
 * (already unchecked - see {@code ApifyClientException}) propagates as-is.
 */
public final class TestAsync {
  private TestAsync() {}

  /** Blocks for {@code future}'s result, unwrapping a {@link CompletionException} if it fails. */
  public static <T> T await(CompletableFuture<T> future) {
    try {
      return future.join();
    } catch (CompletionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw e;
    }
  }
}
