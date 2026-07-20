package com.apify.client.requestqueue;

import com.apify.client.ApifyResource;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A single request stored in a request queue. Fields left {@code null} are omitted when the request
 * is sent to the API.
 */
public final class RequestQueueRequest extends ApifyResource {
  private String id;
  private String url;
  private String uniqueKey;
  private String method;
  private JsonNode userData;

  public RequestQueueRequest() {}

  /** Convenience constructor for the common case of a URL + unique key. */
  public RequestQueueRequest(String url, String uniqueKey) {
    this.url = url;
    this.uniqueKey = uniqueKey;
  }

  /** The unique request ID (assigned by the API; absent on create). */
  public String getId() {
    return id;
  }

  public RequestQueueRequest setId(String id) {
    this.id = id;
    return this;
  }

  /** The request URL. */
  public String getUrl() {
    return url;
  }

  public RequestQueueRequest setUrl(String url) {
    this.url = url;
    return this;
  }

  /** The deduplication key for the request. */
  public String getUniqueKey() {
    return uniqueKey;
  }

  public RequestQueueRequest setUniqueKey(String uniqueKey) {
    this.uniqueKey = uniqueKey;
    return this;
  }

  /** The HTTP method (e.g. {@code "GET"}, {@code "POST"}). */
  public String getMethod() {
    return method;
  }

  public RequestQueueRequest setMethod(String method) {
    this.method = method;
    return this;
  }

  /** Arbitrary user-attached metadata. */
  public JsonNode getUserData() {
    return userData;
  }

  public RequestQueueRequest setUserData(JsonNode userData) {
    this.userData = userData;
    return this;
  }
}
