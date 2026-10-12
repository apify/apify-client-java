package com.apify.client.actor;

import com.apify.client.internal.Json;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.Base64;
import java.util.List;

/**
 * Configures starting an Actor run ({@link ActorClient#start}/{@link ActorClient#call}). All fields
 * are optional.
 *
 * <p>{@link #apply}/{@link #contentTypeOrDefault}/{@link #waitForResourcesSecsValue} are
 * package-private on purpose — see {@link com.apify.client.internal.RunStartSupport}'s class
 * javadoc for why.
 */
public final class ActorStartOptions {

  /**
   * Sentinel for {@link #waitForResources(Long)} (and what {@link #waitForResources(boolean)}
   * passes for {@code true}): retry starting the run until it succeeds, with no time bound.
   */
  public static final long WAIT_FOR_RESOURCES_INDEFINITELY = -1L;

  private String build;
  private Long memoryMbytes;
  private Long timeoutSecs;
  private Long waitForFinish;
  private Long maxItems;
  private Double maxTotalChargeUsd;
  private String contentType;
  private Boolean restartOnError;
  private String forcePermissionLevel;
  private List<Object> webhooks;
  private Long waitForResourcesSecs;

  /** The tag or number of the build to run (e.g. {@code "latest"}, {@code "0.1.2"}). */
  public ActorStartOptions build(String build) {
    this.build = build;
    return this;
  }

  /** Memory in megabytes allocated for the run. */
  public ActorStartOptions memoryMbytes(Long memoryMbytes) {
    this.memoryMbytes = memoryMbytes;
    return this;
  }

  /** Timeout for the run in seconds (0 means no timeout). */
  public ActorStartOptions timeoutSecs(Long timeoutSecs) {
    this.timeoutSecs = timeoutSecs;
    return this;
  }

  /** Maximum seconds to wait server-side for the run to finish (max 60). */
  public ActorStartOptions waitForFinish(Long waitForFinish) {
    this.waitForFinish = waitForFinish;
    return this;
  }

  /** Maximum number of dataset items to charge (pay-per-result Actors). */
  public ActorStartOptions maxItems(Long maxItems) {
    this.maxItems = maxItems;
    return this;
  }

  /** Maximum total charge in USD (pay-per-event Actors). */
  public ActorStartOptions maxTotalChargeUsd(Double maxTotalChargeUsd) {
    this.maxTotalChargeUsd = maxTotalChargeUsd;
    return this;
  }

  /** The content type of the input body. Defaults to {@code application/json}. */
  public ActorStartOptions contentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  /** If {@code true}, restart the run if it fails. */
  public ActorStartOptions restartOnError(Boolean restartOnError) {
    this.restartOnError = restartOnError;
    return this;
  }

  /**
   * Override the Actor's permission level for this run ({@code LIMITED_PERMISSIONS}/{@code
   * FULL_PERMISSIONS}).
   */
  public ActorStartOptions forcePermissionLevel(String forcePermissionLevel) {
    this.forcePermissionLevel = forcePermissionLevel;
    return this;
  }

  /**
   * Ad-hoc webhooks to attach to this run. They are serialized to base64-encoded JSON as the {@code
   * webhooks} query parameter, matching the reference clients.
   */
  public ActorStartOptions webhooks(List<Object> webhooks) {
    this.webhooks = webhooks == null ? null : List.copyOf(webhooks);
    return this;
  }

  /**
   * Retries the start while the account lacks the memory or a concurrent-run slot for the run (the
   * API rejects it with {@code actor-memory-limit-exceeded} or {@code
   * concurrent-runs-limit-exceeded}, both of which clear as other runs or builds finish), waiting
   * 10 seconds between attempts. {@code true} retries until the run starts ({@link
   * #WAIT_FOR_RESOURCES_INDEFINITELY}); {@code false} (the default) does not retry — a
   * resource-limit error is thrown immediately, like any other error.
   */
  public ActorStartOptions waitForResources(boolean waitForResources) {
    this.waitForResourcesSecs = waitForResources ? WAIT_FOR_RESOURCES_INDEFINITELY : null;
    return this;
  }

  /**
   * As {@link #waitForResources(boolean)}, bounding the retrying to at most {@code maxSecs} seconds
   * - once exceeded, the last resource-limit error is thrown. {@code null} disables retrying (the
   * default).
   */
  public ActorStartOptions waitForResources(Long maxSecs) {
    this.waitForResourcesSecs = maxSecs;
    return this;
  }

  String contentTypeOrDefault() {
    return (contentType != null && !contentType.isEmpty())
        ? contentType
        : ResourceContext.CONTENT_TYPE_JSON;
  }

  Long waitForResourcesSecsValue() {
    return waitForResourcesSecs;
  }

  void apply(QueryParams q) {
    q.addString("build", build)
        .addLong("memory", memoryMbytes)
        .addLong("timeout", timeoutSecs)
        .addLong("waitForFinish", waitForFinish)
        .addLong("maxItems", maxItems)
        .addDouble("maxTotalChargeUsd", maxTotalChargeUsd)
        .addBool("restartOnError", restartOnError)
        .addString("forcePermissionLevel", forcePermissionLevel);
    q.addString("webhooks", encodeWebhooks(webhooks));
  }

  /**
   * Encodes an ad-hoc webhooks list as base64-encoded JSON, as the API's {@code webhooks} query
   * parameter requires. Returns {@code null} for a {@code null} list. Shared by Actor and task
   * start options (DRY).
   */
  public static String encodeWebhooks(List<Object> webhooks) {
    if (webhooks == null) {
      return null;
    }
    return Base64.getEncoder().encodeToString(Json.toBytes(webhooks));
  }
}
