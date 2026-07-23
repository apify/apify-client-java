package com.apify.client.user;

import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.Json;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import tools.jackson.databind.JsonNode;

/**
 * A client for accessing user data ({@code /v2/users/{userId}} or {@code /v2/users/me}).
 *
 * <p>For the current user ({@code me}), it also exposes account usage and limits. Those endpoints
 * only exist for {@code me} and throw {@link IllegalStateException} if called on another user's
 * client.
 */
public final class UserClient {

  /** The special user id addressing the currently-authenticated account ({@code /users/me}). */
  public static final String ME = "me";

  private final ResourceContext ctx;
  private final boolean isMe;

  public UserClient(HttpClientCore http, String baseUrl, String id) {
    this.ctx = ResourceContext.single(http, baseUrl, ApiPaths.USERS, id);
    this.isMe = ME.equals(id);
  }

  /**
   * Fetches the user. For {@code me} it returns private account details (via {@link
   * User#getExtra()}); for other users it returns the public profile. Returns empty if the user
   * does not exist.
   */
  public CompletableFuture<Optional<User>> get() {
    return ctx.getResource("", new QueryParams(), User.class);
  }

  /**
   * Fetches the current account's monthly usage for the current month. Only available for {@code
   * me}. Completes with the raw JSON usage report.
   */
  public CompletableFuture<JsonNode> monthlyUsage() {
    return monthlyUsage("");
  }

  /**
   * Fetches the current account's monthly usage for the month containing the given date (formatted
   * as {@code YYYY-MM-DD}). An empty date reports the current month. Only available for {@code me}.
   */
  public CompletableFuture<JsonNode> monthlyUsage(String date) {
    requireMe();
    QueryParams params = new QueryParams();
    if (date != null && !date.isEmpty()) {
      params.addString("date", date);
    }
    return ctx.getResourceRequired("usage/monthly", params, JsonNode.class);
  }

  /** Fetches the current account's resource limits. Only available for {@code me}. */
  public CompletableFuture<JsonNode> limits() {
    requireMe();
    return ctx.getResourceRequired("limits", new QueryParams(), JsonNode.class);
  }

  /** Updates the current account's resource limits. Only available for {@code me}. */
  public CompletableFuture<Void> updateLimits(Object newLimits) {
    requireMe();
    // Route through the shared PUT primitive for consistency with every other resource client's
    // update-style call; the response body (the updated limits) is intentionally not parsed,
    // matching the reference client's void-returning updateLimits.
    return ctx.putRaw(
        "limits", new QueryParams(), Json.toBytes(newLimits), ResourceContext.CONTENT_TYPE_JSON);
  }

  private void requireMe() {
    if (!isMe) {
      throw new IllegalStateException(
          "this operation is only available for the current user (use me())");
    }
  }
}
