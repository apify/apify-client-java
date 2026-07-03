package com.apify.client;

/**
 * Tuning options for {@link RequestQueueClient#batchAddRequests(java.util.List, boolean,
 * BatchAddRequestsOptions)}, mirroring the reference client. Requests the API reports as
 * unprocessed (typically due to rate limiting) are automatically retried with exponential backoff.
 */
public final class BatchAddRequestsOptions {

  /** Default number of retry rounds for unprocessed requests (matches the reference client). */
  static final int DEFAULT_MAX_UNPROCESSED_RETRIES = 3;

  /** Default maximum number of batch API calls made in parallel (matches the reference client). */
  static final int DEFAULT_MAX_PARALLEL = 5;

  /** Default minimum delay before retrying unprocessed requests (matches the reference client). */
  static final long DEFAULT_MIN_DELAY_BETWEEN_UNPROCESSED_RETRIES_MILLIS = 500;

  private int maxUnprocessedRequestsRetries = DEFAULT_MAX_UNPROCESSED_RETRIES;
  private int maxParallel = DEFAULT_MAX_PARALLEL;
  private long minDelayBetweenUnprocessedRequestsRetriesMillis =
      DEFAULT_MIN_DELAY_BETWEEN_UNPROCESSED_RETRIES_MILLIS;

  /** Maximum number of retry attempts for requests the API leaves unprocessed. Default is 3. */
  public BatchAddRequestsOptions maxUnprocessedRequestsRetries(int maxUnprocessedRequestsRetries) {
    this.maxUnprocessedRequestsRetries = Math.max(0, maxUnprocessedRequestsRetries);
    return this;
  }

  /** Maximum number of batch API calls made in parallel. Default is 5. */
  public BatchAddRequestsOptions maxParallel(int maxParallel) {
    this.maxParallel = Math.max(1, maxParallel);
    return this;
  }

  /** Minimum delay before retrying unprocessed requests, in milliseconds. Default is 500. */
  public BatchAddRequestsOptions minDelayBetweenUnprocessedRequestsRetriesMillis(long millis) {
    this.minDelayBetweenUnprocessedRequestsRetriesMillis = Math.max(0, millis);
    return this;
  }

  int maxUnprocessedRequestsRetriesValue() {
    return maxUnprocessedRequestsRetries;
  }

  int maxParallelValue() {
    return maxParallel;
  }

  long minDelayBetweenUnprocessedRequestsRetriesMillisValue() {
    return minDelayBetweenUnprocessedRequestsRetriesMillis;
  }
}
