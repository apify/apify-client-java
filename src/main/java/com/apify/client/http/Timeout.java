package com.apify.client.http;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * The timeout of a single API request attempt: one of the preconfigured {@linkplain Tier tiers}, an
 * exact duration, or {@link #NO_TIMEOUT}.
 *
 * <p>Every client method picks the tier that fits the expected duration of its request: {@link
 * #SHORT} for simple metadata reads and writes, {@link #MEDIUM} for listing, batch and trigger
 * operations, {@link #LONG} for downloads, uploads and streaming. The duration of each tier is set
 * on the {@link com.apify.client.ApifyClientBuilder}. Pass a {@code Timeout} to {@link
 * com.apify.client.ApifyClient#withTimeout(Timeout)} to replace the tier of every request a client
 * sends.
 *
 * <p>A tier or an explicit duration above the builder's {@code timeoutMax} is capped at it, and the
 * client logs a warning when that happens. The timeout applies to each attempt separately and
 * doubles with every retry, up to the same cap. With {@link #NO_TIMEOUT} the client never aborts
 * the request, so a connection that stalls is not retried either.
 */
public final class Timeout {

  /** The preconfigured timeout tiers. */
  public enum Tier {
    /** Simple metadata reads and writes (default 5 seconds). */
    SHORT,
    /** Listing, batch and trigger operations (default 30 seconds). */
    MEDIUM,
    /** Downloads, uploads and streaming (default 360 seconds). */
    LONG
  }

  /** The {@link Tier#SHORT} tier. */
  public static final Timeout SHORT = new Timeout(Tier.SHORT, null, false);

  /** The {@link Tier#MEDIUM} tier. */
  public static final Timeout MEDIUM = new Timeout(Tier.MEDIUM, null, false);

  /** The {@link Tier#LONG} tier. */
  public static final Timeout LONG = new Timeout(Tier.LONG, null, false);

  /** Lets the request run for as long as it takes. */
  public static final Timeout NO_TIMEOUT = new Timeout(null, null, true);

  private final Tier tier;
  private final Duration duration;
  private final boolean noTimeout;

  private Timeout(Tier tier, Duration duration, boolean noTimeout) {
    this.tier = tier;
    this.duration = duration;
    this.noTimeout = noTimeout;
  }

  /**
   * An exact per-attempt timeout.
   *
   * @throws IllegalArgumentException if {@code duration} is null, zero or negative; use {@link
   *     #NO_TIMEOUT} to run without a timeout
   */
  public static Timeout of(Duration duration) {
    if (duration == null || duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(
          "timeout must be strictly positive (use Timeout.NO_TIMEOUT to run without one)");
    }
    return new Timeout(null, duration, false);
  }

  /** An exact per-attempt timeout in seconds; see {@link #of(Duration)}. */
  public static Timeout ofSeconds(long seconds) {
    return of(Duration.ofSeconds(seconds));
  }

  /** The tier this timeout names, or empty for an exact duration and for {@link #NO_TIMEOUT}. */
  public Optional<Tier> tier() {
    return Optional.ofNullable(tier);
  }

  /** The exact duration, or empty for a tier and for {@link #NO_TIMEOUT}. */
  public Optional<Duration> duration() {
    return Optional.ofNullable(duration);
  }

  /** Whether this is {@link #NO_TIMEOUT}. */
  public boolean isNoTimeout() {
    return noTimeout;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Timeout other
        && tier == other.tier
        && noTimeout == other.noTimeout
        && Objects.equals(duration, other.duration);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tier, duration, noTimeout);
  }

  @Override
  public String toString() {
    if (noTimeout) {
      return "Timeout.NO_TIMEOUT";
    }
    return tier != null ? "Timeout." + tier : "Timeout.of(" + duration + ")";
  }
}
