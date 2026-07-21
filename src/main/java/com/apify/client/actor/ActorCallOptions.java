package com.apify.client.actor;

import com.apify.client.log.StreamedLogOptions;
import java.util.List;

/**
 * Configures {@link ActorClient#call(Object, ActorCallOptions, Long)}.
 *
 * <p>Mirrors {@link ActorStartOptions} (delegating every field to an internal instance) but omits
 * {@code waitForFinish}: that field asks the API to hold the HTTP response open server-side while
 * the run finishes, which is redundant with (and wastes a request slot next to) {@code call}'s own
 * client-side {@code waitSecs} polling. This matches the reference client, whose dedicated {@code
 * ActorCallOptions} type is {@code Omit<ActorStartOptions, 'waitForFinish'>}.
 *
 * <p>By default, {@code call} also streams the run's log to a default per-run logger for the
 * duration of the wait (via {@link com.apify.client.run.RunClient#getStreamedLog()}), matching the
 * reference client's {@code options.log} defaulting to {@code 'default'}. Use {@link
 * #disableLogStreaming()} to opt out, or {@link #logOptions(StreamedLogOptions)} for a custom
 * destination.
 *
 * <p>{@link #toStartOptions}/{@link #logStreamingEnabledValue}/{@link #logOptionsValue} are
 * package-private on purpose — see {@link com.apify.client.internal.RunStartSupport}'s class
 * javadoc for why.
 */
public final class ActorCallOptions {

  private final ActorStartOptions startOptions = new ActorStartOptions();
  private boolean logStreamingEnabled = true;
  private StreamedLogOptions logOptions;

  /** The tag or number of the build to run (e.g. {@code "latest"}, {@code "0.1.2"}). */
  public ActorCallOptions build(String build) {
    startOptions.build(build);
    return this;
  }

  /** Memory in megabytes allocated for the run. */
  public ActorCallOptions memoryMbytes(Long memoryMbytes) {
    startOptions.memoryMbytes(memoryMbytes);
    return this;
  }

  /** Timeout for the run in seconds (0 means no timeout). */
  public ActorCallOptions timeoutSecs(Long timeoutSecs) {
    startOptions.timeoutSecs(timeoutSecs);
    return this;
  }

  /** Maximum number of dataset items to charge (pay-per-result Actors). */
  public ActorCallOptions maxItems(Long maxItems) {
    startOptions.maxItems(maxItems);
    return this;
  }

  /** Maximum total charge in USD (pay-per-event Actors). */
  public ActorCallOptions maxTotalChargeUsd(Double maxTotalChargeUsd) {
    startOptions.maxTotalChargeUsd(maxTotalChargeUsd);
    return this;
  }

  /** The content type of the input body. Defaults to {@code application/json}. */
  public ActorCallOptions contentType(String contentType) {
    startOptions.contentType(contentType);
    return this;
  }

  /** If {@code true}, restart the run if it fails. */
  public ActorCallOptions restartOnError(Boolean restartOnError) {
    startOptions.restartOnError(restartOnError);
    return this;
  }

  /**
   * Override the Actor's permission level for this run ({@code LIMITED_PERMISSIONS}/{@code
   * FULL_PERMISSIONS}).
   */
  public ActorCallOptions forcePermissionLevel(String forcePermissionLevel) {
    startOptions.forcePermissionLevel(forcePermissionLevel);
    return this;
  }

  /** Ad-hoc webhooks to attach to this run (serialized to base64-encoded JSON). */
  public ActorCallOptions webhooks(List<Object> webhooks) {
    startOptions.webhooks(webhooks);
    return this;
  }

  /**
   * Disables the default log streaming, matching the reference client's {@code log: null}. {@code
   * call} then only starts the run and polls for completion, as if built from {@link
   * ActorStartOptions} directly.
   */
  public ActorCallOptions disableLogStreaming() {
    this.logStreamingEnabled = false;
    return this;
  }

  /**
   * Customizes log streaming (destination, prefix, whether to include pre-existing log lines)
   * instead of using the default per-run logger. Implies streaming is enabled.
   */
  public ActorCallOptions logOptions(StreamedLogOptions logOptions) {
    this.logOptions = logOptions;
    this.logStreamingEnabled = true;
    return this;
  }

  ActorStartOptions toStartOptions() {
    return startOptions;
  }

  boolean logStreamingEnabledValue() {
    return logStreamingEnabled;
  }

  StreamedLogOptions logOptionsValue() {
    return logOptions;
  }
}
