package com.apify.client.internal;

/**
 * The Apify API's top-level resource-collection path segments (e.g. {@code datasets} in {@code
 * /v2/datasets/{id}}), named and collected in one place so every resource client that needs its own
 * collection path references the same constant instead of repeating the literal.
 *
 * <p>Nested, resource-relative sub-paths (e.g. an Actor's {@code builds}, a run's {@code
 * key-value-store}) are not listed here: they are specific to their parent resource client (e.g.
 * {@code ActorClient}, {@code RunClient}) and are defined right where they are used.
 */
public final class ApiPaths {

  public static final String ACTORS = "actors";
  public static final String ACTOR_BUILDS = "actor-builds";
  public static final String ACTOR_RUNS = "actor-runs";
  public static final String ACTOR_TASKS = "actor-tasks";
  public static final String DATASETS = "datasets";
  public static final String KEY_VALUE_STORES = "key-value-stores";
  public static final String REQUEST_QUEUES = "request-queues";
  public static final String SCHEDULES = "schedules";
  public static final String WEBHOOKS = "webhooks";
  public static final String WEBHOOK_DISPATCHES = "webhook-dispatches";
  public static final String STORE = "store";
  public static final String USERS = "users";
  public static final String LOGS = "logs";

  private ApiPaths() {}
}
