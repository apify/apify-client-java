package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.apify.client.ApifyClient;
import com.apify.client.log.StreamedLog;
import com.apify.client.log.StreamedLogOptions;
import com.apify.client.run.RunClient;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Shared setup for the integration test suite.
 *
 * <p>All integration tests require a valid {@code APIFY_TOKEN} for the test account. The API base
 * URL is taken from {@code APIFY_API_URL} (which includes the {@code /v2} suffix) and falls back to
 * {@code https://api.apify.com/v2}.
 *
 * <p>Tests are designed to run concurrently — including against the same test account from several
 * language clients at once — so every test creates uniquely-named resources and cleans them up.
 */
public abstract class IntegrationBase {

  /** The integration-test contract fallback base URL. */
  static final String DEFAULT_API_URL = "https://api.apify.com/v2";

  /**
   * The {@code waitSecs} budget used across this suite for a live {@code apify/hello-world} run (a
   * "store Actor finishes in a couple of seconds" test fixture): generous enough to absorb
   * queueing/build delays on the shared test account without making a genuinely stuck run block the
   * suite indefinitely.
   */
  static final long TEST_ACTOR_WAIT_SECS = 120L;

  /**
   * Bounded window given to a just-finished run's live log stream to catch up and flush its last
   * bytes: {@code streamedLogRedirection}-style assertions can otherwise race the background reader
   * thread, which may not have pulled any bytes off the stream yet even though the log content
   * itself is already fully available server-side (see {@code
   * ActorRunIntegrationTest#streamedLogRedirection}). Shared by every test that needs the same
   * catch-up wait, via {@link #STREAM_CATCH_UP_ATTEMPTS} + {@link #pollUntil}.
   */
  static final long STREAM_CATCH_UP_TIMEOUT_MILLIS = 15_000;

  /** Poll interval used while waiting out {@link #STREAM_CATCH_UP_TIMEOUT_MILLIS}. */
  static final long STREAM_CATCH_UP_POLL_MILLIS = 250;

  /** {@link #pollUntil} attempt count equivalent to {@link #STREAM_CATCH_UP_TIMEOUT_MILLIS}. */
  static final int STREAM_CATCH_UP_ATTEMPTS =
      (int) (STREAM_CATCH_UP_TIMEOUT_MILLIS / STREAM_CATCH_UP_POLL_MILLIS);

  /**
   * {@link #pollUntil} attempt count bounding how long a CRUD-flow test waits for a just-created
   * resource to surface in its own top-level collection {@code list()} (a write and the LIST
   * endpoint's index can converge asynchronously - the same class of eventual-consistency race as
   * {@link #STREAM_CATCH_UP_ATTEMPTS} and {@code ActorRunIntegrationTest#RUN_LIST_FIND_ATTEMPTS},
   * just with a shorter budget since these are exclusively-owned resources, not entries in a
   * heavily-shared public collection).
   */
  static final int LIST_FIND_ATTEMPTS = 5;

  /** Poll interval used while waiting out {@link #LIST_FIND_ATTEMPTS}. */
  static final long LIST_FIND_BACKOFF_MILLIS = 500L;

  private static final SecureRandom RANDOM = new SecureRandom();

  /**
   * Derives the client base URL from an optional {@code APIFY_API_URL}. The variable includes the
   * {@code /v2} suffix (per the integration-test contract) and falls back to {@link
   * #DEFAULT_API_URL}. Since the client appends {@code /v2} itself, the suffix is stripped.
   */
  static String resolveBaseUrl(String apiUrl) {
    if (apiUrl == null || apiUrl.isEmpty()) {
      apiUrl = DEFAULT_API_URL;
    }
    String trimmed = apiUrl;
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    if (trimmed.endsWith("/v2")) {
      trimmed = trimmed.substring(0, trimmed.length() - "/v2".length());
    }
    return trimmed;
  }

  /** Returns a configured client, or skips the test if {@code APIFY_TOKEN} is unset. */
  static ApifyClient requireClient() {
    String token = System.getenv("APIFY_TOKEN");
    assumeTrue(token != null && !token.isEmpty(), "skipping: APIFY_TOKEN is not set");
    return ApifyClient.builder()
        .token(token)
        .baseUrl(resolveBaseUrl(System.getenv("APIFY_API_URL")))
        .build();
  }

  /**
   * Generates a collision-resistant resource name for test isolation. The random component lets the
   * same test run in parallel (across processes and languages) without clobbering shared state.
   */
  static String uniqueName(String prefix) {
    byte[] buf = new byte[6];
    RANDOM.nextBytes(buf);
    StringBuilder hex = new StringBuilder();
    for (byte b : buf) {
      hex.append(String.format("%02x", b));
    }
    return "java-test-" + prefix + "-" + hex;
  }

  /**
   * Polls {@code check} up to {@code maxAttempts} times, sleeping {@code backoffMillis} between
   * attempts, returning as soon as it reports success (or once attempts are exhausted). Shared by
   * every eventual-consistency wait in this suite (a write and a LIST/iterate endpoint's index can
   * converge asynchronously, so a single-pass check right after a write can race that convergence)
   * so the retry/backoff shape lives in exactly one place. Swallows {@link InterruptedException} by
   * restoring the interrupt flag and returning {@code false} early, so callers don't need to
   * declare a checked exception just for this bounded, best-effort wait.
   */
  public static boolean pollUntil(int maxAttempts, long backoffMillis, BooleanSupplier check) {
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      if (check.getAsBoolean()) {
        return true;
      }
      if (attempt + 1 < maxAttempts) {
        try {
          Thread.sleep(backoffMillis);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    return false;
  }

  /**
   * Opens a fresh {@link StreamedLog} against an already-finished run's static log (with {@link
   * StreamedLogOptions#fromStart(boolean)} so the run's already-past-relative-to-construction
   * messages are not filtered out) and waits up to {@link #STREAM_CATCH_UP_TIMEOUT_MILLIS} for it
   * to deliver the (now non-racing) content. Used by {@link #assertStreamedLogNonEmptyIfProduced}
   * to retry once when a live-tail stream came up empty despite the run having produced a log:
   * opening this stream strictly after the run is already finished means it is a plain GET against
   * a static, fully-persisted log, not a live tail, so it cannot race the run's own writer the way
   * the original stream could.
   */
  static List<String> collectFinishedRunLog(RunClient runClient) {
    List<String> retryCollected = new CopyOnWriteArrayList<>();
    try (StreamedLog retryStream =
        runClient.getStreamedLog(
            new StreamedLogOptions().toLog(retryCollected::add).fromStart(true))) {
      retryStream.start();
      pollUntil(
          STREAM_CATCH_UP_ATTEMPTS, STREAM_CATCH_UP_POLL_MILLIS, () -> !retryCollected.isEmpty());
    }
    return retryCollected;
  }

  /**
   * Asserts that {@code collected} - lines captured by a live-tail {@link StreamedLog} (or a {@code
   * call(...)} overload's built-in log streaming) for a now-finished run - is non-empty, but only
   * when the run actually produced log output at all, per the authoritative, statically-persisted
   * log ({@code runClient.log().get()}).
   *
   * <p>A fast Actor/task run (the store Actors this suite exercises routinely finish in a couple of
   * seconds) can complete - and any log-streaming lifecycle tied to the call/wait can close -
   * before the background reader has pulled any bytes off the live log stream yet, even though the
   * log content itself is already fully available server-side once the run is done. This method
   * first gives {@code collected} a bounded {@link #STREAM_CATCH_UP_TIMEOUT_MILLIS} window to catch
   * up in-place. If it is still empty, it consults the authoritative persisted log to find out
   * whether the run produced any log output at all: if it did not, there is nothing to have
   * streamed and the assertion is skipped entirely (this is not a race, just an Actor that logged
   * nothing); if it did, one more attempt is made via {@link #collectFinishedRunLog} (a brand-new,
   * non-racing stream) before failing.
   */
  static void assertStreamedLogNonEmptyIfProduced(RunClient runClient, List<String> collected) {
    pollUntil(STREAM_CATCH_UP_ATTEMPTS, STREAM_CATCH_UP_POLL_MILLIS, () -> !collected.isEmpty());

    Optional<String> authoritativeLog = runClient.log().get();
    boolean runProducedLog = authoritativeLog.isPresent() && !authoritativeLog.get().isEmpty();
    if (runProducedLog && collected.isEmpty()) {
      collected.addAll(collectFinishedRunLog(runClient));
    }
    if (runProducedLog) {
      assertTrue(
          !collected.isEmpty(),
          "run produced a non-empty log ("
              + authoritativeLog.get().length()
              + " chars) but the streamed collector observed none - log streaming/redirection did"
              + " not work");
    }
  }
}
