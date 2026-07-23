package com.apify.client.internal;

/**
 * Unwraps the top-level {@code {"data": ...}} wrapper used by most Apify endpoints. Internal.
 *
 * @param <T> the type of the wrapped payload
 */
public final class DataEnvelope<T> {
  private T data;

  /** The unwrapped {@code data} payload. */
  public T getData() {
    return data;
  }
}
