package com.apify.client.run;

import com.apify.client.PaginationList;
import com.apify.client.dataset.Dataset;
import com.apify.client.dataset.DatasetClient;
import com.apify.client.dataset.DatasetListItemsOptions;
import com.apify.client.internal.DatasetItemsOptionsAccess;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import tools.jackson.databind.JsonNode;

/**
 * The paging state machine behind {@link RunClient#iterateDatasetItems}: it reads a run's default
 * dataset while the run is still producing items, one page per {@link #next()} call.
 *
 * <p>While the run has not finished, each round reads the rows below the dataset's {@code
 * itemCount} and then waits up to the poll interval for the run to finish, so the last rows are
 * read as soon as it does. Each page is requested with a {@code limit} that ends at {@code
 * itemCount}, so it covers exactly the rows it asks for, whatever the filters or {@code unwind} do
 * to the items. {@code itemCount} lags a few seconds behind the pushed items, so once the run is
 * terminal the rows past it are read a page at a time until none are left.
 *
 * <p>Not thread-safe: the publisher that drives it fetches one page at a time.
 */
final class RunDatasetItems<T> {

  private final RunClient runClient;
  private final DatasetListItemsOptions options;
  private final Class<T> itemClass;
  private final DatasetItemsOptionsAccess access;
  private final long pageSize;
  private final long pollIntervalSecs;

  /** The first row this iteration must not read, or {@code null} for no limit. */
  private final Long end;

  private enum Phase {
    START,
    POLL,
    TAIL,
    DONE
  }

  private Phase phase = Phase.START;
  private long position;
  private long itemCount;
  private boolean finished;
  private DatasetClient dataset;
  private RunClient resolvedRun;

  RunDatasetItems(
      RunClient runClient,
      DatasetListItemsOptions options,
      long pageSize,
      long pollIntervalSecs,
      Class<T> itemClass) {
    this.runClient = runClient;
    this.access = DatasetItemsOptionsAccess.get();
    this.options = access.snapshot(options);
    this.itemClass = itemClass;
    this.pageSize = pageSize;
    this.pollIntervalSecs = pollIntervalSecs;
    this.position = access.offset(this.options);
    Long limit = access.limit(this.options);
    this.end = limit != null && limit > 0 ? position + limit : null;
  }

  /**
   * Reads the next page. Completes with a page whose scanned count is non-zero while there may be
   * more rows, and with an empty page that scanned nothing once iteration is over.
   */
  CompletableFuture<PaginationList<T>> next() {
    switch (phase) {
      case START:
        return runClient.get().thenCompose(run -> start(run)).thenCompose(unused -> next());
      case POLL:
        return poll();
      case TAIL:
        return tail();
      default:
        return CompletableFuture.completedFuture(endOfData());
    }
  }

  private CompletableFuture<Void> start(Optional<ActorRun> run) {
    // A last-run client resolves runs/last on each request, so a newer run would swap the dataset
    // mid-iteration; pin the run its first request resolves to.
    resolvedRun = run.isPresent() ? runClient.pinned(run.get()) : runClient;
    dataset = resolvedRun.dataset();
    finished = run.isEmpty() || run.get().isTerminal();
    phase = Phase.POLL;
    return refreshItemCount();
  }

  private CompletableFuture<Void> refreshItemCount() {
    return dataset
        .get()
        .thenAccept(
            ds -> {
              long count = ds.map(Dataset::getItemCount).orElse(0L);
              itemCount = end != null ? Math.min(count, end) : count;
            });
  }

  private CompletableFuture<PaginationList<T>> poll() {
    if (position < itemCount) {
      long limit = Math.min(pageSize, itemCount - position);
      return readPage(limit);
    }
    if (end != null && position >= end) {
      phase = Phase.DONE;
      return CompletableFuture.completedFuture(endOfData());
    }
    if (finished) {
      phase = Phase.TAIL;
      return tail();
    }
    return resolvedRun
        .waitForFinish(pollIntervalSecs)
        .thenCompose(
            run -> {
              finished = run.isTerminal();
              return refreshItemCount();
            })
        .thenCompose(unused -> next());
  }

  private CompletableFuture<PaginationList<T>> tail() {
    long limit = end != null ? Math.min(pageSize, end - position) : pageSize;
    return page(position, limit)
        .thenCompose(
            page -> {
              Long scanned = page.getScannedCount();
              boolean empty = page.getItems().isEmpty() && (scanned == null || scanned == 0);
              if (!empty) {
                return CompletableFuture.completedFuture(advance(page, limit));
              }
              if (!access.dropsRows(options)) {
                return finish();
              }
              // A page that clean, skipEmpty or unwind emptied past a lagging itemCount reports no
              // scanned rows either, so a read of the next row without those filters (they would
              // empty it again) tells whether any rows are left.
              return dataset
                  .listItems(access.plainPage(position, 1L), JsonNode.class)
                  .thenCompose(
                      probe ->
                          probe.getItems().isEmpty()
                              ? finish()
                              : CompletableFuture.completedFuture(advance(page, limit)));
            });
  }

  private CompletableFuture<PaginationList<T>> finish() {
    phase = Phase.DONE;
    return CompletableFuture.completedFuture(endOfData());
  }

  private CompletableFuture<PaginationList<T>> readPage(long limit) {
    return page(position, limit).thenApply(page -> advance(page, limit));
  }

  private CompletableFuture<PaginationList<T>> page(long offset, long limit) {
    return dataset.listItems(access.page(options, offset, limit), itemClass);
  }

  /** Moves past the {@code limit} rows a page covered and marks it as having scanned them. */
  private PaginationList<T> advance(PaginationList<T> page, long limit) {
    position += limit;
    if (end != null && position >= end && phase == Phase.TAIL) {
      phase = Phase.DONE;
    }
    page.setScannedCount(Math.max(limit, 1));
    return page;
  }

  private PaginationList<T> endOfData() {
    PaginationList<T> page = new PaginationList<>();
    page.setItems(List.of());
    page.setScannedCount(0L);
    return page;
  }
}
