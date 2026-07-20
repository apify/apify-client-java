package com.apify.client.internal;

/**
 * The Apify API's top-level resource collection path segments, in one place so the same literal is
 * never duplicated between {@code ApifyClient} (which passes some of them to a resource client's
 * constructor) and the resource clients that otherwise hardcode their own path. Internal to the
 * client — not the URL paths of the OpenAPI spec verbatim, just the single path segment each
 * resource client is rooted at (e.g. {@code https://api.apify.com/v2/}{@value #DATASETS}{@code
 * /{id}}).
 */
public final class ResourcePaths {

  public static final String ACTORS = "actors";
  public static final String ACTOR_BUILDS = "actor-builds";
  public static final String ACTOR_RUNS = "actor-runs";
  public static final String ACTOR_TASKS = "actor-tasks";
  public static final String DATASETS = "datasets";
  public static final String KEY_VALUE_STORES = "key-value-stores";
  public static final String REQUEST_QUEUES = "request-queues";
  public static final String SCHEDULES = "schedules";
  public static final String STORE = "store";
  public static final String USERS = "users";
  public static final String WEBHOOKS = "webhooks";
  public static final String WEBHOOK_DISPATCHES = "webhook-dispatches";
  public static final String LOGS = "logs";

  private ResourcePaths() {}
}
