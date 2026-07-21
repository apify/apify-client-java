package com.apify.client.integration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.apify.client.ApifyClient;
import java.security.SecureRandom;
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
}
