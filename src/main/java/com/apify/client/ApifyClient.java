package com.apify.client;

import com.apify.client.actor.ActorClient;
import com.apify.client.actor.ActorCollectionClient;
import com.apify.client.build.BuildClient;
import com.apify.client.build.BuildCollectionClient;
import com.apify.client.dataset.DatasetClient;
import com.apify.client.dataset.DatasetCollectionClient;
import com.apify.client.http.DefaultApifyHttpClient;
import com.apify.client.http.HttpClient;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.ResourcePaths;
import com.apify.client.keyvalue.KeyValueStoreClient;
import com.apify.client.keyvalue.KeyValueStoreCollectionClient;
import com.apify.client.log.LogClient;
import com.apify.client.requestqueue.RequestQueueClient;
import com.apify.client.requestqueue.RequestQueueCollectionClient;
import com.apify.client.run.ActorRun;
import com.apify.client.run.RunClient;
import com.apify.client.run.RunCollectionClient;
import com.apify.client.schedule.ScheduleClient;
import com.apify.client.schedule.ScheduleCollectionClient;
import com.apify.client.store.StoreCollectionClient;
import com.apify.client.task.TaskClient;
import com.apify.client.task.TaskCollectionClient;
import com.apify.client.user.UserClient;
import com.apify.client.webhook.WebhookClient;
import com.apify.client.webhook.WebhookCollectionClient;
import com.apify.client.webhook.WebhookDispatchClient;
import com.apify.client.webhook.WebhookDispatchCollectionClient;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The entry point for interacting with the Apify API.
 *
 * <p><b>Official, but experimental — AI-generated and AI-maintained.</b> Review the code before
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
 *   <li>Replaceable transport: the {@link HttpClient} interface, with a default {@link
 *       DefaultApifyHttpClient}; swap it via {@link ApifyClientBuilder#httpBackend(HttpClient)}.
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

  /**
   * Returns the {@code User-Agent} header value this client sends on every API call. Exposed for
   * introspection/debugging (e.g. logging it alongside a request, or reusing the same value on an
   * adjacent raw HTTP call for consistent observability) — the client itself does not need callers
   * to read this back.
   */
  public String getUserAgent() {
    return http.userAgent();
  }

  /**
   * Returns the fully-qualified API base URL this client targets (including the {@code /v2}
   * suffix). Exposed for introspection/debugging (e.g. confirming which environment a configured
   * client points at, or building an adjacent raw HTTP call against the same base URL) — the client
   * itself does not need callers to read this back.
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
    return new BuildCollectionClient(http, baseUrl, ResourcePaths.ACTOR_BUILDS);
  }

  /** A client for a specific Actor build. */
  public BuildClient build(String id) {
    return new BuildClient(http, baseUrl, id);
  }

  // ----- Run accessors -------------------------------------------------------

  /** A client for the Actor run collection (list runs). */
  public RunCollectionClient runs() {
    return new RunCollectionClient(http, baseUrl, ResourcePaths.ACTOR_RUNS);
  }

  /** A client for a specific Actor run. */
  public RunClient run(String id) {
    return new RunClient(this, http, baseUrl, ResourcePaths.ACTOR_RUNS, id);
  }

  // ----- Dataset accessors ---------------------------------------------------

  /** A client for the dataset collection (list &amp; get-or-create datasets). */
  public DatasetCollectionClient datasets() {
    return new DatasetCollectionClient(http, baseUrl);
  }

  /** A client for a specific dataset, addressed by ID or name. */
  public DatasetClient dataset(String id) {
    return new DatasetClient(http, baseUrl, ResourcePaths.DATASETS, id, publicBaseUrl);
  }

  // ----- Key-value store accessors -------------------------------------------

  /** A client for the key-value store collection. */
  public KeyValueStoreCollectionClient keyValueStores() {
    return new KeyValueStoreCollectionClient(http, baseUrl);
  }

  /** A client for a specific key-value store, addressed by ID or name. */
  public KeyValueStoreClient keyValueStore(String id) {
    return new KeyValueStoreClient(
        http, baseUrl, ResourcePaths.KEY_VALUE_STORES, id, publicBaseUrl);
  }

  // ----- Request queue accessors ---------------------------------------------

  /** A client for the request queue collection. */
  public RequestQueueCollectionClient requestQueues() {
    return new RequestQueueCollectionClient(http, baseUrl);
  }

  /** A client for a specific request queue, addressed by ID or name. */
  public RequestQueueClient requestQueue(String id) {
    return new RequestQueueClient(http, baseUrl, ResourcePaths.REQUEST_QUEUES, id);
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
    return new WebhookDispatchCollectionClient(http, baseUrl, ResourcePaths.WEBHOOK_DISPATCHES);
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
    return new LogClient(http, baseUrl, ResourcePaths.LOGS, buildOrRunId);
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
   * variable, so it only works when called from inside an Actor run. This mirrors the reference
   * JavaScript client's equivalent helper, which reads the same platform-injected variable — code
   * running as an Actor already has {@code ACTOR_RUN_ID} in its environment, so there is nothing
   * else meaningful to pass here (the alternative, {@link #run(String)}{@code .update(...)}, is
   * always available for updating a run by an explicit, arbitrary ID). If {@code isTerminal} is
   * true, the message becomes final and won't be overwritten. Throws {@link IllegalStateException}
   * if {@code ACTOR_RUN_ID} is not set.
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
