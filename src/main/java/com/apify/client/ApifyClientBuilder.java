package com.apify.client;

import com.apify.client.http.DefaultHttpTransport;
import com.apify.client.http.HttpTransport;
import com.apify.client.http.RetryConfig;
import com.apify.client.internal.HttpClientCore;
import java.time.Duration;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Builder for {@link ApifyClient}. Obtain one via {@link ApifyClient#builder()}, configure it, then
 * call {@link #build()}.
 */
public final class ApifyClientBuilder {

  /** Default base URL of the Apify API (without the {@code /v2} suffix). */
  static final String DEFAULT_BASE_URL = "https://api.apify.com";

  static final int DEFAULT_MAX_RETRIES = 8;
  static final Duration DEFAULT_MIN_DELAY = Duration.ofMillis(500);
  static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(360);

  /** Environment variable that signals the client is running on the Apify platform. */
  static final String ENV_IS_AT_HOME = "APIFY_IS_AT_HOME";

  private String token;
  private String baseUrl = DEFAULT_BASE_URL;
  private String publicBaseUrl;
  private int maxRetries = DEFAULT_MAX_RETRIES;
  private Duration minDelayBetweenRetries = DEFAULT_MIN_DELAY;
  private Duration maxDelayBetweenRetries = DEFAULT_TIMEOUT;
  private Duration timeout = DEFAULT_TIMEOUT;
  private String userAgentSuffix;
  private HttpTransport httpTransport;
  private BooleanSupplier isAtHomeFn = ApifyClientBuilder::defaultIsAtHome;

  ApifyClientBuilder() {}

  /** Sets the API token used for authentication (sent as a Bearer token). */
  public ApifyClientBuilder token(String token) {
    this.token = token;
    return this;
  }

  /** Overrides the base URL of the API. The {@code /v2} suffix is appended automatically. */
  public ApifyClientBuilder baseUrl(String baseUrl) {
    this.baseUrl = requireNonBlank(baseUrl, "baseUrl");
    return this;
  }

  /**
   * Overrides the base URL used when building public, shareable resource URLs (e.g. a signed
   * dataset-items URL). Defaults to the API base URL. The {@code /v2} suffix is appended
   * automatically.
   */
  public ApifyClientBuilder publicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = requireNonBlank(publicBaseUrl, "publicBaseUrl");
    return this;
  }

  /** Sets the maximum number of retries for failed requests (default 8). */
  public ApifyClientBuilder maxRetries(int maxRetries) {
    this.maxRetries = requireNonNegative(maxRetries, "maxRetries");
    return this;
  }

  /** Sets the minimum delay between retries (default 500ms). */
  public ApifyClientBuilder minDelayBetweenRetries(Duration minDelayBetweenRetries) {
    this.minDelayBetweenRetries =
        requireNonNegative(minDelayBetweenRetries, "minDelayBetweenRetries");
    return this;
  }

  /** Sets the maximum (exponentially-grown) delay between retries (default equals the timeout). */
  public ApifyClientBuilder maxDelayBetweenRetries(Duration maxDelayBetweenRetries) {
    this.maxDelayBetweenRetries =
        requireNonNegative(maxDelayBetweenRetries, "maxDelayBetweenRetries");
    return this;
  }

  /**
   * Sets the maximum per-attempt request (socket) timeout (default 360s): the ceiling each retry
   * attempt's timeout grows toward, not a wall-clock bound on the cumulative time across retries.
   * The connection-establishment timeout is separate and configured on the {@link HttpTransport}
   * (see {@link DefaultHttpTransport#DefaultHttpTransport(Duration)}).
   */
  public ApifyClientBuilder timeout(Duration timeout) {
    this.timeout = requirePositive(timeout, "timeout");
    return this;
  }

  /** Appends a custom suffix to the {@code User-Agent} header. */
  public ApifyClientBuilder userAgentSuffix(String userAgentSuffix) {
    this.userAgentSuffix = userAgentSuffix;
    return this;
  }

  /** Replaces the default HTTP transport with a custom implementation. */
  public ApifyClientBuilder httpTransport(HttpTransport httpTransport) {
    if (httpTransport == null) {
      throw new IllegalArgumentException("httpTransport must not be null");
    }
    this.httpTransport = httpTransport;
    return this;
  }

  /** Validates a required, non-blank string argument, returning it unchanged. */
  private static String requireNonBlank(String value, String argName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(argName + " must not be null or blank");
    }
    return value;
  }

  /** Validates a non-negative {@code int} argument, returning it unchanged. */
  private static int requireNonNegative(int value, String argName) {
    if (value < 0) {
      throw new IllegalArgumentException(argName + " must not be negative");
    }
    return value;
  }

  /** Validates a non-null, non-negative {@link Duration} argument, returning it unchanged. */
  private static Duration requireNonNegative(Duration value, String argName) {
    if (value == null || value.isNegative()) {
      throw new IllegalArgumentException(argName + " must not be null or negative");
    }
    return value;
  }

  /**
   * Validates a non-null, strictly-positive {@link Duration} argument, returning it unchanged.
   * Unlike the delay parameters (where zero legitimately means "retry immediately"), a zero {@code
   * timeout} is not tolerated: it is passed straight through to {@code HttpRequest.Builder#timeout}
   * deep inside {@link DefaultHttpTransport}, which rejects a zero/negative duration at
   * request-send time — so a zero value here would build a client that fails on its very first call
   * rather than at construction time.
   */
  private static Duration requirePositive(Duration value, String argName) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(argName + " must be strictly positive");
    }
    return value;
  }

  /** Test seam: overrides how the client decides the {@code isAtHome} User-Agent flag. */
  ApifyClientBuilder isAtHomeFn(BooleanSupplier isAtHomeFn) {
    this.isAtHomeFn = isAtHomeFn;
    return this;
  }

  /** The API path suffix every base URL is normalized to end with. */
  private static final String API_VERSION_PATH = "/v2";

  /** Builds the configured {@link ApifyClient}. */
  public ApifyClient build() {
    HttpTransport transport = httpTransport != null ? httpTransport : new DefaultHttpTransport();
    String userAgent = buildUserAgent(userAgentSuffix, isAtHomeFn);
    RetryConfig retry =
        new RetryConfig(maxRetries, minDelayBetweenRetries, maxDelayBetweenRetries, timeout);
    HttpClientCore http = new HttpClientCore(transport, token, userAgent, retry);

    String apiBase = normalizeApiUrl(baseUrl);
    String publicBase = normalizeApiUrl(publicBaseUrl != null ? publicBaseUrl : baseUrl);
    return new ApifyClient(http, apiBase, publicBase);
  }

  /** Strips any trailing slashes and appends {@link #API_VERSION_PATH}. */
  private static String normalizeApiUrl(String url) {
    return trimTrailingSlash(url) + API_VERSION_PATH;
  }

  private static String trimTrailingSlash(String s) {
    String result = s;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }

  /**
   * Reports whether the client is running on the Apify platform, by reading the {@code
   * APIFY_IS_AT_HOME} environment variable (set to a non-empty value on the platform). Per the
   * client requirements the flag is based solely on this variable, matching the JS reference.
   */
  static boolean defaultIsAtHome() {
    String v = System.getenv(ENV_IS_AT_HOME);
    return v != null && !v.isEmpty();
  }

  /**
   * Builds the {@code User-Agent} header value mandated by the client requirements: {@code
   * ApifyClient/{version} ({os}; Java/{javaVersion}); isAtHome/{true|false}}.
   */
  static String buildUserAgent(String suffix, BooleanSupplier isAtHomeFn) {
    String os = platformToken();
    String javaVersion = System.getProperty("java.version", "unknown");
    String atHome = isAtHomeFn.getAsBoolean() ? "true" : "false";
    String ua =
        "ApifyClient/"
            + Version.CLIENT_VERSION
            + " ("
            + os
            + "; Java/"
            + javaVersion
            + "); isAtHome/"
            + atHome;
    if (suffix != null && !suffix.isEmpty()) {
      ua += "; " + suffix;
    }
    return ua;
  }

  /**
   * Returns the short, lowercase OS token used in the {@code User-Agent}. It matches the
   * identifiers emitted by the reference JS client's {@code os.platform()} (Node) — {@code linux},
   * {@code darwin}, {@code win32}, {@code android}, etc. — so all Apify clients report the platform
   * uniformly. Java's {@code os.name} system property returns human-readable names ({@code Linux},
   * {@code Mac OS X}, {@code Windows 10}), so it is mapped to the aligned token here.
   */
  static String platformToken() {
    return platformToken(System.getProperty("os.name", ""), System.getProperty("java.vm.name", ""));
  }

  /**
   * Maps the given {@code os.name} / {@code java.vm.name} property values to the aligned platform
   * token. Split out from {@link #platformToken()} as a pure function so the mapping can be tested
   * for every platform without depending on the host the tests run on.
   */
  static String platformToken(String osName, String vmName) {
    // Android runs on a Linux kernel (os.name == "Linux"); detect it via its VM name ("Dalvik").
    String vm = vmName.toLowerCase(Locale.ROOT);
    if (vm.contains("dalvik")) {
      return "android";
    }
    String os = osName.toLowerCase(Locale.ROOT);
    // macOS/Darwin is checked before Windows because the literal "darwin" contains "win"; the
    // reverse order would misclassify a "Darwin" os.name as "win32".
    if (os.contains("mac") || os.contains("darwin")) {
      return "darwin";
    }
    if (os.contains("win")) {
      return "win32";
    }
    if (os.contains("nux") || os.contains("nix")) {
      return "linux";
    }
    if (os.contains("aix")) {
      return "aix";
    }
    if (os.contains("sunos") || os.contains("solaris")) {
      return "sunos";
    }
    if (os.contains("freebsd")) {
      return "freebsd";
    }
    if (os.contains("openbsd")) {
      return "openbsd";
    }
    // Fallback: the first whitespace-delimited word, lowercased, to keep the token short and
    // stable.
    return os.isEmpty() ? "unknown" : os.split("\\s+")[0];
  }
}
