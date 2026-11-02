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
 * <p>It also exposes the user's monthly usage and account limits ({@code
 * /v2/users/{userId}/usage/monthly} and {@code /v2/users/{userId}/limits}), for {@code me} or for a
 * user id the API token may access.
 */
public final class UserClient {

  /** The special user id addressing the currently-authenticated account ({@code /users/me}). */
  public static final String ME = "me";

  private final ResourceContext ctx;

  public UserClient(HttpClientCore http, String baseUrl, String id) {
    this.ctx = ResourceContext.single(http, baseUrl, ApiPaths.USERS, id);
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
   * Fetches the user's monthly usage for the current month. Completes with the raw JSON usage
   * report.
   */
  public CompletableFuture<JsonNode> monthlyUsage() {
    return monthlyUsage("");
  }

  /**
   * Fetches the user's monthly usage for the month containing the given date (formatted as {@code
   * YYYY-MM-DD}). An empty date reports the current month.
   */
  public CompletableFuture<JsonNode> monthlyUsage(String date) {
    QueryParams params = new QueryParams();
    if (date != null && !date.isEmpty()) {
      params.addString("date", date);
    }
    return ctx.getResourceRequired("usage/monthly", params, JsonNode.class);
  }

  /** Fetches the user's account and usage limits. */
  public CompletableFuture<JsonNode> limits() {
    return ctx.getResourceRequired("limits", new QueryParams(), JsonNode.class);
  }

  /** Updates the user's account and usage limits. */
  public CompletableFuture<Void> updateLimits(Object newLimits) {
    // Route through the shared PUT primitive for consistency with every other resource client's
    // update-style call; the response body (the updated limits) is intentionally not parsed,
    // matching the reference client's void-returning updateLimits.
    return ctx.putRaw(
        "limits", new QueryParams(), Json.toBytes(newLimits), ResourceContext.CONTENT_TYPE_JSON);
  }
}
