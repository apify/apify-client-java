package com.apify.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The entry point for interacting with the Apify API.
 *
 * <p><b>Official, but experimental — AI-generated and AI-maintained.</b> This is an official Apify
 * client, but it is experimental: it is generated and maintained by AI. Review the code before
 * relying on it in production and report issues on the repository.
 *
 * <p>Construct it with {@link #create(String)} (token-only) or {@link #builder()}, then obtain
 * resource clients via the accessor methods, e.g. {@link #actor(String)}, {@link #dataset(String)},
 * {@link #run(String)}. It is safe for concurrent use.
 *
 * <h2>Quick start</h2>
 *
 * <pre>{@code
 * ApifyClient client = ApifyClient.create("my-api-token");
 * ActorRun run = client.actor("apify/hello-world").call(null, new ActorStartOptions(), null);
 * PaginationList<JsonNode> items = client.dataset(run.getDefaultDatasetId())
 *     .listItems(new DatasetListItemsOptions());
 * }</pre>
 *
 * <h2>Architecture</h2>
 *
 * <ul>
 *   <li>Public interface: {@link ApifyClient} and the resource clients it returns.
 *   <li>Replaceable transport: the {@link HttpBackend} interface, with a default {@link
 *       DefaultHttpBackend}; swap it via {@link ApifyClientBuilder#httpBackend(HttpBackend)}.
 *   <li>Cross-cutting behaviour (auth, User-Agent, retries with exponential backoff, timeouts)
 *       lives in the internal HTTP client and is applied to every request.
 * </ul>
 */
public final class ApifyClient {

  /** Addresses the current user ({@code /users/me}). */
  private static final String ME_USER_PLACEHOLDER = "me";

  private final HttpClientCore http;
  private final String baseUrl;
  private final String publicBaseUrl;

  ApifyClient(HttpClientCore http, String baseUrl, String publicBaseUrl) {
    this.http = http;
    this.baseUrl = baseUrl;
    this.publicBaseUrl = publicBaseUrl;
  }

  /** Creates a client authenticated with the given API token and default settings. */
  public static ApifyClient create(String token) {
    return builder().token(token).build();
  }

  /** Returns a new {@link ApifyClientBuilder} for configuring a client. */
  public static ApifyClientBuilder builder() {
    return new ApifyClientBuilder();
  }

  /** Returns the {@code User-Agent} header value this client sends. */
  public String getUserAgent() {
    return http.userAgent();
  }

  /**
   * Returns the fully-qualified API base URL this client targets (including the {@code /v2}
   * suffix).
   */
  public String getApiBaseUrl() {
    return baseUrl;
  }

  // ----- Actor accessors -----------------------------------------------------

  /** A client for the Actor collection (list &amp; create Actors). */
  public ActorCollectionClient actors() {
    return new ActorCollectionClient(http, baseUrl);
  }

  /** A client for a specific Actor, addressed by ID or {@code username~name}. */
  public ActorClient actor(String id) {
    return new ActorClient(this, http, baseUrl, id);
  }

  // ----- Build accessors -----------------------------------------------------

  /** A client for the Actor build collection (list builds). */
  public BuildCollectionClient builds() {
    return new BuildCollectionClient(http, baseUrl, "actor-builds");
  }

  /** A client for a specific Actor build. */
  public BuildClient build(String id) {
    return new BuildClient(http, baseUrl, id);
  }

  // ----- Run accessors -------------------------------------------------------

  /** A client for the Actor run collection (list runs). */
  public RunCollectionClient runs() {
    return new RunCollectionClient(http, baseUrl, "actor-runs");
  }

  /** A client for a specific Actor run. */
  public RunClient run(String id) {
    return new RunClient(this, http, baseUrl, "actor-runs", id);
  }

  // ----- Dataset accessors ---------------------------------------------------

  /** A client for the dataset collection (list &amp; get-or-create datasets). */
  public DatasetCollectionClient datasets() {
    return new DatasetCollectionClient(http, baseUrl);
  }

  /** A client for a specific dataset, addressed by ID or name. */
  public DatasetClient dataset(String id) {
    return new DatasetClient(http, baseUrl, "datasets", id).withPublicBase(publicBaseUrl);
  }

  // ----- Key-value store accessors -------------------------------------------

  /** A client for the key-value store collection. */
  public KeyValueStoreCollectionClient keyValueStores() {
    return new KeyValueStoreCollectionClient(http, baseUrl);
  }

  /** A client for a specific key-value store, addressed by ID or name. */
  public KeyValueStoreClient keyValueStore(String id) {
    return new KeyValueStoreClient(http, baseUrl, "key-value-stores", id)
        .withPublicBase(publicBaseUrl);
  }

  // ----- Request queue accessors ---------------------------------------------

  /** A client for the request queue collection. */
  public RequestQueueCollectionClient requestQueues() {
    return new RequestQueueCollectionClient(http, baseUrl);
  }

  /** A client for a specific request queue, addressed by ID or name. */
  public RequestQueueClient requestQueue(String id) {
    return new RequestQueueClient(http, baseUrl, "request-queues", id);
  }

  // ----- Task accessors ------------------------------------------------------

  /** A client for the Actor task collection (list &amp; create tasks). */
  public TaskCollectionClient tasks() {
    return new TaskCollectionClient(http, baseUrl);
  }

  /** A client for a specific Actor task. */
  public TaskClient task(String id) {
    return new TaskClient(this, http, baseUrl, id);
  }

  // ----- Schedule accessors --------------------------------------------------

  /** A client for the schedule collection (list &amp; create schedules). */
  public ScheduleCollectionClient schedules() {
    return new ScheduleCollectionClient(http, baseUrl);
  }

  /** A client for a specific schedule. */
  public ScheduleClient schedule(String id) {
    return new ScheduleClient(http, baseUrl, id);
  }

  // ----- Webhook accessors ---------------------------------------------------

  /** A client for the webhook collection (list &amp; create webhooks). */
  public WebhookCollectionClient webhooks() {
    return new WebhookCollectionClient(http, baseUrl);
  }

  /** A client for a specific webhook. */
  public WebhookClient webhook(String id) {
    return new WebhookClient(http, baseUrl, id);
  }

  /** A client for the webhook dispatch collection. */
  public WebhookDispatchCollectionClient webhookDispatches() {
    return new WebhookDispatchCollectionClient(http, baseUrl, "webhook-dispatches");
  }

  /** A client for a specific webhook dispatch. */
  public WebhookDispatchClient webhookDispatch(String id) {
    return new WebhookDispatchClient(http, baseUrl, id);
  }

  // ----- Misc accessors ------------------------------------------------------

  /** A client for browsing the Apify Store. */
  public StoreCollectionClient store() {
    return new StoreCollectionClient(http, baseUrl);
  }

  /** A client for accessing a build's or run's log. */
  public LogClient log(String buildOrRunId) {
    return new LogClient(http, baseUrl, "logs", buildOrRunId);
  }

  /** A client for the current user ({@code /users/me}). */
  public UserClient me() {
    return new UserClient(http, baseUrl, ME_USER_PLACEHOLDER);
  }

  /** A client for a specific user by ID or username. */
  public UserClient user(String id) {
    return new UserClient(http, baseUrl, id);
  }

  /**
   * Sets the status message of the current Actor run.
   *
   * <p>This convenience method updates the run identified by the {@code ACTOR_RUN_ID} environment
   * variable, so it only works when called from inside an Actor run. If {@code isTerminal} is true,
   * the message becomes final and won't be overwritten. Throws {@link IllegalStateException} if
   * {@code ACTOR_RUN_ID} is not set.
   */
  public ActorRun setStatusMessage(String message, boolean isTerminal) {
    String runId = System.getenv("ACTOR_RUN_ID");
    if (runId == null || runId.isEmpty()) {
      throw new IllegalStateException("ACTOR_RUN_ID environment variable is not set");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("statusMessage", message);
    body.put("isStatusMessageTerminal", isTerminal);
    return run(runId).update(body);
  }
}
