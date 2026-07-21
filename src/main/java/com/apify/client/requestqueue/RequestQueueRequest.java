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
  private Instant lockExpiresAt;

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

  /** Sets the unique request ID. */
  public RequestQueueRequest setId(String id) {
    this.id = id;
    return this;
  }

  /** The request URL. */
  public String getUrl() {
    return url;
  }

  /** Sets the request URL. */
  public RequestQueueRequest setUrl(String url) {
    this.url = url;
    return this;
  }

  /** The deduplication key for the request. */
  public String getUniqueKey() {
    return uniqueKey;
  }

  /** Sets the deduplication key for the request. */
  public RequestQueueRequest setUniqueKey(String uniqueKey) {
    this.uniqueKey = uniqueKey;
    return this;
  }

  /** The HTTP method (e.g. {@code "GET"}, {@code "POST"}). */
  public String getMethod() {
    return method;
  }

  /** Sets the HTTP method. */
  public RequestQueueRequest setMethod(String method) {
    this.method = method;
    return this;
  }

  /**
   * Arbitrary user-attached metadata ({@code null} if unset). Returns a defensive deep copy, so
   * mutating the returned {@link JsonNode} (if it is a container type such as {@code ObjectNode})
   * cannot affect this request's own state, matching {@link #getHeaders()}/{@link
   * #getErrorMessages()}.
   */
  public JsonNode getUserData() {
    return userData == null ? null : userData.deepCopy();
  }

  /** Sets the user-attached metadata (defensively deep-copied; see {@link #getUserData()}). */
  public RequestQueueRequest setUserData(JsonNode userData) {
    this.userData = userData == null ? null : userData.deepCopy();
    return this;
  }

  /** The HTTP request body, if any. */
  public String getPayload() {
    return payload;
  }

  /** Sets the HTTP request body. */
  public RequestQueueRequest setPayload(String payload) {
    this.payload = payload;
    return this;
  }

  /** Custom HTTP headers to send with the request (unmodifiable; {@code null} if unset). */
  public Map<String, String> getHeaders() {
    return headers == null ? null : Collections.unmodifiableMap(headers);
  }

  /** Sets the custom HTTP headers to send with the request (defensively copied). */
  public RequestQueueRequest setHeaders(Map<String, String> headers) {
    this.headers = headers == null ? null : new LinkedHashMap<>(headers);
    return this;
  }

  /** If {@code true}, the request is not retried on failure. */
  public Boolean getNoRetry() {
    return noRetry;
  }

  /** Sets whether the request is retried on failure. */
  public RequestQueueRequest setNoRetry(Boolean noRetry) {
    this.noRetry = noRetry;
    return this;
  }

  /** When the request was marked as handled, or {@code null} if it has not been. */
  public Instant getHandledAt() {
    return handledAt;
  }

  /** Sets when the request was marked as handled. */
  public RequestQueueRequest setHandledAt(Instant handledAt) {
    this.handledAt = handledAt;
    return this;
  }

  /** Number of times processing this request has already been retried. */
  public Integer getRetryCount() {
    return retryCount;
  }

  /** Sets the number of times processing this request has already been retried. */
  public RequestQueueRequest setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
    return this;
  }

  /** The URL actually loaded, after following redirects (may differ from {@link #getUrl()}). */
  public String getLoadedUrl() {
    return loadedUrl;
  }

  /** Sets the URL actually loaded, after following redirects. */
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

  /** Sets the error messages recorded from previous failed processing attempts. */
  public RequestQueueRequest setErrorMessages(List<String> errorMessages) {
    this.errorMessages = errorMessages == null ? null : List.copyOf(errorMessages);
    return this;
  }

  /**
   * When this request's lock (if any) expires. Only populated on items returned from {@link
   * RequestQueueClient#listAndLockHead}; {@code null} everywhere else, including on write.
   */
  public Instant getLockExpiresAt() {
    return lockExpiresAt;
  }
}
