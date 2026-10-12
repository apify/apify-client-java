package com.apify.client.task;

import com.apify.client.actor.ActorStartOptions;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.List;

/**
 * Configures starting a task run ({@link TaskClient#start}/{@link TaskClient#call}).
 *
 * <p>It mirrors {@link ActorStartOptions} but omits the fields the task run endpoint does not
 * accept (the Actor-only {@code contentType} and {@code forcePermissionLevel}), matching the
 * reference client.
 *
 * <p>{@link #apply}/{@link #contentTypeOrDefault}/{@link #waitForResourcesSecsValue} are
 * package-private on purpose — see {@link com.apify.client.internal.RunStartSupport}'s class
 * javadoc for why.
 */
public final class TaskStartOptions {
  private String build;
  private Long memoryMbytes;
  private Long timeoutSecs;
  private Long waitForFinish;
  private Long maxItems;
  private Double maxTotalChargeUsd;
  private Boolean restartOnError;
  private List<Object> webhooks;
  private Long waitForResourcesSecs;

  /** The tag or number of the build to run (e.g. {@code "latest"}, {@code "0.1.2"}). */
  public TaskStartOptions build(String build) {
    this.build = build;
    return this;
  }

  /** Memory in megabytes allocated for the run. */
  public TaskStartOptions memoryMbytes(Long memoryMbytes) {
    this.memoryMbytes = memoryMbytes;
    return this;
  }

  /** Timeout for the run in seconds (0 means no timeout). */
  public TaskStartOptions timeoutSecs(Long timeoutSecs) {
    this.timeoutSecs = timeoutSecs;
    return this;
  }

  /** Maximum seconds to wait server-side for the run to finish (max 60). */
  public TaskStartOptions waitForFinish(Long waitForFinish) {
    this.waitForFinish = waitForFinish;
    return this;
  }

  /** Maximum number of dataset items to charge (pay-per-result Actors). */
  public TaskStartOptions maxItems(Long maxItems) {
    this.maxItems = maxItems;
    return this;
  }

  /** Maximum total charge in USD (pay-per-event Actors). */
  public TaskStartOptions maxTotalChargeUsd(Double maxTotalChargeUsd) {
    this.maxTotalChargeUsd = maxTotalChargeUsd;
    return this;
  }

  /** If {@code true}, restart the run if it fails. */
  public TaskStartOptions restartOnError(Boolean restartOnError) {
    this.restartOnError = restartOnError;
    return this;
  }

  /** Ad-hoc webhooks to attach to this run (serialized to base64-encoded JSON). */
  public TaskStartOptions webhooks(List<Object> webhooks) {
    this.webhooks = webhooks == null ? null : List.copyOf(webhooks);
    return this;
  }

  /**
   * Retries the start while the account lacks the memory or a concurrent-run slot for the run (the
   * API rejects it with {@code actor-memory-limit-exceeded} or {@code
   * concurrent-runs-limit-exceeded}, both of which clear as other runs or builds finish), waiting
   * 10 seconds between attempts. {@code true} retries until the run starts ({@link
   * ActorStartOptions#WAIT_FOR_RESOURCES_INDEFINITELY}); {@code false} (the default) does not retry
   * — a resource-limit error is thrown immediately, like any other error.
   */
  public TaskStartOptions waitForResources(boolean waitForResources) {
    this.waitForResourcesSecs =
        waitForResources ? ActorStartOptions.WAIT_FOR_RESOURCES_INDEFINITELY : null;
    return this;
  }

  /**
   * As {@link #waitForResources(boolean)}, bounding the retrying to at most {@code maxSecs} seconds
   * - once exceeded, the last resource-limit error is thrown. {@code null} disables retrying (the
   * default).
   */
  public TaskStartOptions waitForResources(Long maxSecs) {
    this.waitForResourcesSecs = maxSecs;
    return this;
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
        .addBool("restartOnError", restartOnError);
    q.addString("webhooks", ActorStartOptions.encodeWebhooks(webhooks));
  }

  /**
   * The task run endpoint does not accept a content-type override (unlike the Actor run endpoint),
   * so this always returns the JSON default; present only so {@code start}/{@code call} can be
   * driven generically for both Actor and task runs (both call sites read this the same way).
   */
  String contentTypeOrDefault() {
    return ResourceContext.CONTENT_TYPE_JSON;
  }
}
