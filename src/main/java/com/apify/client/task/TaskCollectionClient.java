package com.apify.client.task;

import com.apify.client.ListOptions;
import com.apify.client.internal.AbstractCollectionClient;
import com.apify.client.internal.ApiPaths;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;

/** A client for the Actor task collection ({@code GET/POST /v2/actor-tasks}). */
public final class TaskCollectionClient extends AbstractCollectionClient<Task, ListOptions> {

  public TaskCollectionClient(HttpClientCore http, String baseUrl) {
    super(
        ResourceContext.collection(http, baseUrl, ApiPaths.ACTOR_TASKS),
        Task.class,
        ListOptions::new);
  }

  /** Creates a new task. {@code task} is any JSON-serializable task definition. */
  public Task create(Object task) {
    return ctx.createResource(new QueryParams(), task, Task.class);
  }
}
