package com.apify.client.actor;

import com.apify.client.ListOptions;
import com.apify.client.PaginationList;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.Iterator;
import java.util.List;

/** A client for an Actor's version collection ({@code GET/POST /v2/actors/{actorId}/versions}). */
public final class ActorVersionCollectionClient {
  private final ResourceContext ctx;

  ActorVersionCollectionClient(HttpClientCore http, String actorUrl) {
    this.ctx = ResourceContext.collection(http, actorUrl, "versions");
  }

  /**
   * Lists the Actor's versions. {@code options} is accepted only for signature consistency with
   * every other collection's {@code list(ListOptions)} (matching the reference client, whose {@code
   * list()} likewise accepts but never forwards an options argument here) — {@code GET
   * .../versions} takes no query parameters at all, so nothing in {@code options} (including {@code
   * offset}/{@code limit}/{@code desc}) is sent to the server. Pass {@code null} for the common
   * case; use {@link #iterate(ListOptions)}'s {@code limit} to cap the number returned client-side.
   */
  public PaginationList<ActorVersion> list(ListOptions options) {
    return ctx.listResource("", new QueryParams(), ActorVersion.class);
  }

  /**
   * Returns an iterator over the Actor's versions. Unlike the paginated collection iterators, this
   * fetches eagerly: the single request runs when {@code iterate} is called, not on first {@code
   * next()}. That's because {@code GET /v2/actors/{actorId}/versions} is not offset/limit paginated
   * at all — it takes no pagination parameters and always returns the full version list in one
   * {@code {total, items}} response, so routing it through the offset/limit paging engine would
   * loop forever (the server returns the same non-empty page at every offset; this is why the
   * sibling non-paginated {@code env-vars} collection is also a single-fetch iterator). The
   * options' {@code limit} still caps the number yielded ({@code null} or non-positive = all);
   * {@code offset} has no effect and there is no page size to tune.
   */
  public Iterator<ActorVersion> iterate(ListOptions options) {
    ListOptions opts = options != null ? options : new ListOptions();
    List<ActorVersion> items = list(opts).getItems();
    Long limit = opts.limitValue();
    if (limit != null && limit > 0 && items.size() > limit) {
      items = items.subList(0, (int) (long) limit);
    }
    return items.iterator();
  }

  /** Creates a new Actor version. {@code version} is any JSON-serializable version definition. */
  public ActorVersion create(Object version) {
    return ctx.createResource(new QueryParams(), version, ActorVersion.class);
  }
}
