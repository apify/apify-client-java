package com.apify.client.internal;

import com.apify.client.dataset.DatasetListItemsOptions;

/**
 * Cross-package access to the item options of {@link DatasetListItemsOptions}, whose accessors are
 * package-private because only the client itself needs them. {@code DatasetListItemsOptions}
 * registers its implementation when it is loaded, so code in another package (the run client's
 * dataset iteration) can read and derive options without those accessors being public API. Internal
 * to the client.
 */
public interface DatasetItemsOptionsAccess {

  /** The registered implementation; the options class is loaded before it is looked up. */
  static DatasetItemsOptionsAccess get() {
    // Loading the options class runs its static initializer, which registers the implementation.
    new DatasetListItemsOptions();
    return Holder.instance;
  }

  /** Registers the implementation; called once by {@link DatasetListItemsOptions}. */
  static void register(DatasetItemsOptionsAccess access) {
    Holder.instance = access;
  }

  /** The first row {@code options} asks for, or 0. */
  long offset(DatasetListItemsOptions options);

  /** The maximum number of rows {@code options} asks for, or {@code null}. */
  Long limit(DatasetListItemsOptions options);

  /**
   * Whether {@code clean}, {@code skipEmpty} or {@code unwind} is set: the server-side filters that
   * can leave a page of existing rows without a single returned item.
   */
  boolean dropsRows(DatasetListItemsOptions options);

  /**
   * An independent copy of {@code options} (the defaults for {@code null}) for reading rows in the
   * order they were pushed, so a later change of the caller's options cannot reach the iteration.
   */
  DatasetListItemsOptions snapshot(DatasetListItemsOptions options);

  /** A copy of {@code options} for the given page of rows, in the order they were pushed. */
  DatasetListItemsOptions page(DatasetListItemsOptions options, long offset, long limit);

  /** Options for the given page of rows with none of the item filters of the caller. */
  DatasetListItemsOptions plainPage(long offset, long limit);

  /** Holds the registered implementation. */
  final class Holder {
    private static volatile DatasetItemsOptionsAccess instance;

    private Holder() {}
  }
}
