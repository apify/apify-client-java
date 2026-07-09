package com.apify.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Set;

/**
 * The lifecycle status of an Actor run or build (the API's {@code ActorJobStatus}).
 *
 * <p>Some statuses are <em>terminal</em> — once reached, the run/build is finished and its status
 * will not change. Use {@link #isTerminal()} to test for this rather than comparing strings.
 *
 * <p>{@link #UNKNOWN} is returned for any status value the API introduces that this client version
 * does not yet recognise, so deserialization never fails on a newer server.
 */
public enum RunStatus {
  /** The run/build has been created but has not started yet. */
  READY("READY"),
  /** The run/build is currently executing. */
  RUNNING("RUNNING"),
  /** The run/build finished successfully (terminal). */
  SUCCEEDED("SUCCEEDED"),
  /** The run/build failed (terminal). */
  FAILED("FAILED"),
  /** The run/build is being timed out. */
  TIMING_OUT("TIMING-OUT"),
  /** The run/build was timed out (terminal). */
  TIMED_OUT("TIMED-OUT"),
  /** The run/build is being aborted. */
  ABORTING("ABORTING"),
  /** The run/build was aborted (terminal). */
  ABORTED("ABORTED"),
  /** A status value the API returned that this client version does not recognise. */
  UNKNOWN(null);

  /** Statuses in which a run/build is finished and will not change. */
  private static final Set<RunStatus> TERMINAL = Set.of(SUCCEEDED, FAILED, ABORTED, TIMED_OUT);

  private final String wireValue;

  RunStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The value used on the wire (e.g. {@code "TIMED-OUT"}); {@code null} for {@link #UNKNOWN}. */
  @JsonValue
  public String getWireValue() {
    return wireValue;
  }

  /** Whether this is a terminal (finished) status. {@link #UNKNOWN} is never terminal. */
  public boolean isTerminal() {
    return TERMINAL.contains(this);
  }

  /** Maps a wire value to its constant, or {@link #UNKNOWN} for an unrecognised non-null value. */
  @JsonCreator
  public static RunStatus fromWire(String value) {
    if (value == null) {
      return null;
    }
    for (RunStatus status : values()) {
      if (value.equals(status.wireValue)) {
        return status;
      }
    }
    return UNKNOWN;
  }
}
