package com.apify.client.run;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Metadata about how an {@link ActorRun} was initiated. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ActorRunMeta {
  private String origin;
  private String clientIp;
  private String userAgent;

  /** What triggered the run (e.g. {@code "WEB"}, {@code "API"}, {@code "SCHEDULER"}). */
  public String getOrigin() {
    return origin;
  }

  /** The IP address of the client that started the run, if known. */
  public String getClientIp() {
    return clientIp;
  }

  /** The {@code User-Agent} of the client that started the run. */
  public String getUserAgent() {
    return userAgent;
  }
}
