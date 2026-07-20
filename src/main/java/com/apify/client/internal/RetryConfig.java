package com.apify.client.internal;

import java.time.Duration;

/** Retry/timeout policy for the orchestrating HTTP client. Internal to the client. */
public final class RetryConfig {
  /** Maximum number of retries (the request is attempted up to {@code maxRetries + 1} times). */
  final int maxRetries;

  /** Minimum delay between retries; doubled on each subsequent retry (exponential backoff). */
  final Duration minDelayBetweenRetries;

  /** Upper bound on the (exponentially growing) inter-retry delay. */
  final Duration maxDelayBetweenRetries;

  /**
   * Maximum per-attempt request (socket) timeout, and the default base timeout for a call. Each
   * attempt's timeout may grow from a smaller per-call base but is capped at this value. This
   * bounds a single attempt, not the cumulative time across all retries.
   */
  final Duration timeout;

  public RetryConfig(
      int maxRetries,
      Duration minDelayBetweenRetries,
      Duration maxDelayBetweenRetries,
      Duration timeout) {
    this.maxRetries = maxRetries;
    this.minDelayBetweenRetries = minDelayBetweenRetries;
    this.maxDelayBetweenRetries = maxDelayBetweenRetries;
    this.timeout = timeout;
  }
}
