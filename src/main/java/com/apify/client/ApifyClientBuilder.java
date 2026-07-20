package com.apify.client;

import com.apify.client.http.DefaultApifyHttpClient;
import com.apify.client.http.HttpClient;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.RetryConfig;
import java.time.Duration;
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

  /**
   * Default per-attempt request timeout, deliberately generous (matching the reference JS client):
   * it bounds a single HTTP round-trip, which for this client can mean uploading/downloading a
   * large dataset or key-value-store payload, or a long-polling {@code waitForFinish} call — not
   * just a quick metadata fetch. Lower it via {@link #timeout(Duration)} for latency-sensitive
   * calls; {@link ApifyClient} does not offer separate per-call-shape timeouts, since every call
   * already shares this one configurable ceiling and the client has no way to know in advance which
   * calls in a given application are "small" versus "large".
   */
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
  private HttpClient httpBackend;
  private BooleanSupplier isAtHomeFn = ApifyClientBuilder::defaultIsAtHome;

  ApifyClientBuilder() {}

  /** Sets the API token used for authentication (sent as a Bearer token). */
  public ApifyClientBuilder token(String token) {
    this.token = token;
    return this;
  }

  /** Overrides the base URL of the API. The {@code /v2} suffix is appended automatically. */
  public ApifyClientBuilder baseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
    return this;
  }

  /**
   * Overrides the base URL used when building public, shareable resource URLs (e.g. a signed
   * dataset-items URL). Defaults to the API base URL. The {@code /v2} suffix is appended
   * automatically.
   */
  public ApifyClientBuilder publicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = publicBaseUrl;
    return this;
  }

  /** Sets the maximum number of retries for failed requests (default 8). */
  public ApifyClientBuilder maxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
    return this;
  }

  /** Sets the minimum delay between retries (default 500ms). */
  public ApifyClientBuilder minDelayBetweenRetries(Duration minDelayBetweenRetries) {
    this.minDelayBetweenRetries = minDelayBetweenRetries;
    return this;
  }

  /** Sets the maximum (exponentially-grown) delay between retries (default equals the timeout). */
  public ApifyClientBuilder maxDelayBetweenRetries(Duration maxDelayBetweenRetries) {
    this.maxDelayBetweenRetries = maxDelayBetweenRetries;
    return this;
  }

  /** Sets the overall per-request timeout (default 360s). */
  public ApifyClientBuilder timeout(Duration timeout) {
    this.timeout = timeout;
    return this;
  }

  /** Appends a custom suffix to the {@code User-Agent} header. */
  public ApifyClientBuilder userAgentSuffix(String userAgentSuffix) {
    this.userAgentSuffix = userAgentSuffix;
    return this;
  }

  /** Replaces the default HTTP backend with a custom implementation (the replaceable transport). */
  public ApifyClientBuilder httpBackend(HttpClient httpBackend) {
    this.httpBackend = httpBackend;
    return this;
  }

  /** Test seam: overrides how the client decides the {@code isAtHome} User-Agent flag. */
  ApifyClientBuilder isAtHomeFn(BooleanSupplier isAtHomeFn) {
    this.isAtHomeFn = isAtHomeFn;
    return this;
  }

  /**
   * Builds the configured {@link ApifyClient}.
   *
   * @throws IllegalArgumentException if a configured value is invalid (a blank base URL, a negative
   *     retry count, or a negative duration) — surfaced here, at configuration time, rather than as
   *     a confusing failure on the client's first API call.
   */
  public ApifyClient build() {
    validate();
    HttpClient backend = httpBackend != null ? httpBackend : new DefaultApifyHttpClient();
    String userAgent = buildUserAgent(userAgentSuffix, isAtHomeFn);
    RetryConfig retry =
        new RetryConfig(maxRetries, minDelayBetweenRetries, maxDelayBetweenRetries, timeout);
    HttpClientCore http = new HttpClientCore(backend, token, userAgent, retry);

    String apiBase = trimTrailingSlash(baseUrl) + "/v2";
    String publicSource = publicBaseUrl != null ? publicBaseUrl : baseUrl;
    String publicBase = trimTrailingSlash(publicSource) + "/v2";
    return new ApifyClient(http, apiBase, publicBase);
  }

  /**
   * Fails fast on configuration mistakes that would otherwise surface as a confusing failure much
   * later (e.g. a malformed request URL, or a retry loop that never sleeps). Does not validate
   * {@code token}: the API itself is the authority on whether a token is required/valid (some
   * proxies or Actor-local setups legitimately run without one).
   */
  private void validate() {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl must not be null or blank");
    }
    if (publicBaseUrl != null && publicBaseUrl.isBlank()) {
      throw new IllegalArgumentException("publicBaseUrl must not be blank when set");
    }
    if (maxRetries < 0) {
      throw new IllegalArgumentException("maxRetries must not be negative: " + maxRetries);
    }
    requireNonNegative(minDelayBetweenRetries, "minDelayBetweenRetries");
    requireNonNegative(maxDelayBetweenRetries, "maxDelayBetweenRetries");
    requireNonNegative(timeout, "timeout");
  }

  private static void requireNonNegative(Duration duration, String name) {
    if (duration == null || duration.isNegative()) {
      throw new IllegalArgumentException(name + " must not be null or negative: " + duration);
    }
  }

  /** Strips every trailing {@code /} from {@code s} (e.g. so {@code /v2} is never doubled up). */
  private static String trimTrailingSlash(String s) {
    while (s.endsWith("/")) {
      s = s.substring(0, s.length() - 1);
    }
    return s;
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
    String vm = vmName.toLowerCase(java.util.Locale.ROOT);
    if (vm.contains("dalvik")) {
      return "android";
    }
    String os = osName.toLowerCase(java.util.Locale.ROOT);
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
