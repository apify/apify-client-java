package com.apify.client.run;

import com.apify.client.ListOptions;
import com.apify.client.PaginationList;
import com.apify.client.internal.AbstractCollectionClient;
import com.apify.client.internal.HttpClientCore;
import com.apify.client.internal.QueryParams;
import com.apify.client.internal.ResourceContext;
import java.util.Iterator;

/**
 * A client for a run collection: the account-wide collection ({@code GET /v2/actor-runs}), an
 * Actor's runs ({@code GET /v2/actors/{id}/runs}), or a task's runs ({@code GET
 * /v2/actor-tasks/{id}/runs}).
 *
 * <p>Extends {@link AbstractCollectionClient} for the shared {@code ctx}/item-class plumbing, but
 * its {@code list}/{@code iterate} take an extra run-specific {@link RunListOptions} filter that
 * the shared {@code list(ListOptions)}/{@code iterate(ListOptions, Long)} (still inherited, and
 * usable when no run filter is needed) cannot express.
 */
public final class RunCollectionClient extends AbstractCollectionClient<ActorRun, ListOptions> {

  public RunCollectionClient(HttpClientCore http, String baseUrl, String resourcePath) {
    super(
        ResourceContext.collection(http, baseUrl, resourcePath), ActorRun.class, ListOptions::new);
  }

  /**
   * Lists runs, applying the standard pagination and the run-specific filters. Both {@code options}
   * and {@code filter} may be {@code null}, which is treated as "no options"/"no filter".
   */
  public PaginationList<ActorRun> list(ListOptions options, RunListOptions filter) {
    QueryParams params = new QueryParams();
    if (options != null) {
      options.apply(params);
    }
    if (filter != null) {
      filter.apply(params);
    }
    return listWithParams(params);
  }

  /**
   * Returns a lazy iterator over the runs, applying the standard pagination and run-specific
   * filters. The options' {@code limit} caps the total number yielded ({@code null} or non-positive
   * = all); {@code chunkSize} is the per-request page size ({@code null} = server default). Both
   * {@code options} and {@code filter} may be {@code null}.
   */
  public Iterator<ActorRun> iterate(ListOptions options, RunListOptions filter) {
    return iterate(options, filter, null);
  }

  /**
   * As {@link #iterate(ListOptions, RunListOptions)}, but {@code chunkSize} sets the per-request
   * page size.
   */
  public Iterator<ActorRun> iterate(ListOptions options, RunListOptions filter, Long chunkSize) {
    ListOptions opts = options != null ? options : new ListOptions();
    return iterateWithFilters(
        opts.limitValue(),
        chunkSize,
        opts.offsetValue(),
        p -> {
          opts.applyFilters(p);
          if (filter != null) {
            filter.apply(p);
          }
        });
  }
}
