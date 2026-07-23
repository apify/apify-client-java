package com.apify.client.webhook;

import com.apify.client.ApifyResource;
import java.time.Instant;

/** A single HTTP delivery attempt made for a {@link WebhookDispatch}. */
public final class WebhookDispatchCall extends ApifyResource {
  private Instant startedAt;
  private Instant finishedAt;
  private String errorMessage;
  private Integer responseStatus;
  private String responseBody;

  /** When this delivery attempt started. */
  public Instant getStartedAt() {
    return startedAt;
  }

  /** When this delivery attempt finished. */
  public Instant getFinishedAt() {
    return finishedAt;
  }

  /**
   * The error message if the attempt failed before receiving a response, otherwise {@code null}.
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /** The HTTP status code returned by the target URL, or {@code null} if none was received. */
  public Integer getResponseStatus() {
    return responseStatus;
  }

  /** The response body returned by the target URL, or {@code null} if none was received. */
  public String getResponseBody() {
    return responseBody;
  }
}
