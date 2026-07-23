package com.apify.client.http;

import java.time.Duration;

/** Retry/timeout policy for the orchestrating HTTP client. Internal to the client. */
public final class RetryConfig {
  /**
   * Maximum number of retries (the request is attempted up to {@code maxRetries + 1} times). Public
   * (not just within this package) so the internal HTTP client, which lives in a separate,
   * non-exported package, can read the policy it applies.
   */
  public final int maxRetries;

  /** Minimum delay between retries; doubled on each subsequent retry (exponential backoff). */
  public final Duration minDelayBetweenRetries;

  /** Upper bound on the (exponentially growing) inter-retry delay. */
  public final Duration maxDelayBetweenRetries;

  /**
   * Maximum per-attempt request (socket) timeout, and the default base timeout for a call. Each
   * attempt's timeout may grow from a smaller per-call base but is capped at this value. This
   * bounds a single attempt, not the cumulative time across all retries.
   */
  public final Duration timeout;

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
