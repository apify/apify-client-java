package com.apify.client;

import java.time.Duration;

/** Retry/timeout policy for the orchestrating HTTP client. Internal to the client. */
final class RetryConfig {
  /** Maximum number of retries (the request is attempted up to {@code maxRetries + 1} times). */
  final int maxRetries;

  /** Minimum delay between retries; doubled on each subsequent retry (exponential backoff). */
  final Duration minDelayBetweenRetries;

  /** Upper bound on the (exponentially growing) inter-retry delay. */
  final Duration maxDelayBetweenRetries;

  /** Overall per-request timeout budget. Each attempt's timeout grows but is capped here. */
  final Duration timeout;

  RetryConfig(
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
