package com.apify.client.http;

import java.time.Duration;

/** Retry/timeout policy for the orchestrating HTTP client. Internal to the client. */
public final class RetryConfig {

  /** Default duration of the {@link Timeout#SHORT} tier. */
  public static final Duration DEFAULT_TIMEOUT_SHORT = Duration.ofSeconds(5);

  /** Default duration of the {@link Timeout#MEDIUM} tier. */
  public static final Duration DEFAULT_TIMEOUT_MEDIUM = Duration.ofSeconds(30);

  /** Default duration of the {@link Timeout#LONG} tier. */
  public static final Duration DEFAULT_TIMEOUT_LONG = Duration.ofSeconds(360);

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

  /** Duration of the {@link Timeout#SHORT} tier. */
  public final Duration timeoutShort;

  /** Duration of the {@link Timeout#MEDIUM} tier. */
  public final Duration timeoutMedium;

  /** Duration of the {@link Timeout#LONG} tier. */
  public final Duration timeoutLong;

  /**
   * Cap on the timeout of a single request attempt. The first attempt gets the requested timeout,
   * each retry doubles it, and none exceeds this value. This bounds a single attempt, not the
   * cumulative time across all retries.
   */
  public final Duration timeout;

  /** A policy with the default tier durations and the given cap on a single attempt. */
  public RetryConfig(
      int maxRetries,
      Duration minDelayBetweenRetries,
      Duration maxDelayBetweenRetries,
      Duration timeout) {
    this(
        maxRetries,
        minDelayBetweenRetries,
        maxDelayBetweenRetries,
        DEFAULT_TIMEOUT_SHORT,
        DEFAULT_TIMEOUT_MEDIUM,
        DEFAULT_TIMEOUT_LONG,
        timeout);
  }

  public RetryConfig(
      int maxRetries,
      Duration minDelayBetweenRetries,
      Duration maxDelayBetweenRetries,
      Duration timeoutShort,
      Duration timeoutMedium,
      Duration timeoutLong,
      Duration timeoutMax) {
    this.maxRetries = maxRetries;
    this.minDelayBetweenRetries = minDelayBetweenRetries;
    this.maxDelayBetweenRetries = maxDelayBetweenRetries;
    this.timeoutShort = timeoutShort;
    this.timeoutMedium = timeoutMedium;
    this.timeoutLong = timeoutLong;
    this.timeout = timeoutMax;
  }
}
