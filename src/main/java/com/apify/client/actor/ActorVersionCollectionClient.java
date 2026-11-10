package com.apify.client.actor;

import com.apify.client.ListOptions;
import com.apify.client.PaginationList;
import com.apify.client.http.Timeout;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.ListPublisher;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/** A client for an Actor's version collection ({@code GET/POST /v2/actors/{actorId}/versions}). */
public final class ActorVersionCollectionClient {
  private final ResourceContext ctx;

  ActorVersionCollectionClient(HttpClientCore http, String actorUrl) {
    this.ctx = ResourceContext.collection(http, actorUrl, "versions");
  }

  /**
   * Lists the Actor's versions. The endpoint takes no query parameters, so {@code options} is
   * accepted only for consistency with other collections and is never sent. Pass {@code null}; to
   * cap the number returned, use the {@code limit} of {@link #iterate(ListOptions)}.
   */
  public CompletableFuture<PaginationList<ActorVersion>> list(ListOptions options) {
    return ctx.listResource("", new QueryParams(), ActorVersion.class, Timeout.SHORT);
  }

  /**
   * Returns a publisher over the Actor's versions. The endpoint is not paginated, so the single
   * request runs when {@code iterate} is called, not on first demand. The {@code limit} of {@code
   * options} caps the number yielded ({@code null} or non-positive means all); {@code offset} has
   * no effect.
   */
  public Flow.Publisher<ActorVersion> iterate(ListOptions options) {
    ListOptions opts = options != null ? options : new ListOptions();
    Long limit = opts.limitValue();
    CompletableFuture<List<ActorVersion>> items =
        list(opts)
            .thenApply(
                page -> {
                  List<ActorVersion> all = page.getItems();
                  return (limit != null && limit > 0 && all.size() > limit)
                      ? all.subList(0, (int) (long) limit)
                      : all;
                });
    return new ListPublisher<>(items);
  }

  /** Creates a new Actor version. {@code version} is any JSON-serializable version definition. */
  public CompletableFuture<ActorVersion> create(Object version) {
    return ctx.createResource(new QueryParams(), version, ActorVersion.class);
  }
}
