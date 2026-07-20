package com.apify.client.requestqueue;

import com.apify.client.ApifyResource;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
  private String payload;
  private Map<String, String> headers;
  private Boolean noRetry;
  private Instant handledAt;
  private Integer retryCount;
  private String loadedUrl;
  private List<String> errorMessages;

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

  /** The HTTP request body, if any. */
  public String getPayload() {
    return payload;
  }

  public RequestQueueRequest setPayload(String payload) {
    this.payload = payload;
    return this;
  }

  /** Custom HTTP headers to send with the request (unmodifiable; {@code null} if unset). */
  public Map<String, String> getHeaders() {
    return headers == null ? null : Collections.unmodifiableMap(headers);
  }

  public RequestQueueRequest setHeaders(Map<String, String> headers) {
    this.headers = headers == null ? null : new LinkedHashMap<>(headers);
    return this;
  }

  /** If {@code true}, the request is not retried on failure. */
  public Boolean getNoRetry() {
    return noRetry;
  }

  public RequestQueueRequest setNoRetry(Boolean noRetry) {
    this.noRetry = noRetry;
    return this;
  }

  /** When the request was marked as handled, or {@code null} if it has not been. */
  public Instant getHandledAt() {
    return handledAt;
  }

  public RequestQueueRequest setHandledAt(Instant handledAt) {
    this.handledAt = handledAt;
    return this;
  }

  /** Number of times processing this request has already been retried. */
  public Integer getRetryCount() {
    return retryCount;
  }

  public RequestQueueRequest setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
    return this;
  }

  /** The URL actually loaded, after following redirects (may differ from {@link #getUrl()}). */
  public String getLoadedUrl() {
    return loadedUrl;
  }

  public RequestQueueRequest setLoadedUrl(String loadedUrl) {
    this.loadedUrl = loadedUrl;
    return this;
  }

  /**
   * Error messages recorded from previous failed processing attempts, oldest first (unmodifiable;
   * {@code null} if unset).
   */
  public List<String> getErrorMessages() {
    return errorMessages == null ? null : Collections.unmodifiableList(errorMessages);
  }

  public RequestQueueRequest setErrorMessages(List<String> errorMessages) {
    this.errorMessages = errorMessages == null ? null : List.copyOf(errorMessages);
    return this;
  }
}
