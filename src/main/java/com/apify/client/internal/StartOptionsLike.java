package com.apify.client.internal;

/**
 * The common shape every "start a run" options type ({@code
 * com.apify.client.actor.ActorStartOptions}, {@code com.apify.client.task.TaskStartOptions})
 * implements, so {@link RunStartSupport} can build the start request generically across both
 * instead of each resource client duplicating the same {@code start}/{@code call} logic.
 */
public interface StartOptionsLike {

  /** Applies every query parameter these options configure (build, memory, timeout, ...). */
  void apply(QueryParams q);

  /** The content type of the input body to send, defaulting to JSON when unset. */
  String contentTypeOrDefault();
}
