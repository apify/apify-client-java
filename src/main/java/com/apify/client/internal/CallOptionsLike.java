package com.apify.client.internal;

import com.apify.client.log.StreamedLogOptions;

/**
 * The common shape every {@code call(...)} options type ({@code
 * com.apify.client.actor.ActorCallOptions}, {@code com.apify.client.task.TaskCallOptions})
 * implements, so {@link RunStartSupport} can drive {@code call}'s "start, then wait, optionally
 * streaming the log" behavior generically across both instead of each resource client duplicating
 * it. {@code S} is the paired start-options type ({@code call} options always mirror "start options
 * minus {@code waitForFinish}").
 */
public interface CallOptionsLike<S extends StartOptionsLike> {

  /** The equivalent start options (every field the two share). */
  S toStartOptions();

  /** Whether {@code call} should stream the run's log for the duration of the wait. */
  boolean logStreamingEnabledValue();

  /** A custom log-streaming destination, or {@code null} to use the default per-run logger. */
  StreamedLogOptions logOptionsValue();
}
