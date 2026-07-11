package com.apify.client;

import java.util.List;

/**
 * Configures listing or downloading dataset items ({@code GET /v2/datasets/{datasetId}/items}). All
 * fields are optional.
 */
public final class DatasetListItemsOptions {
  private Long offset;
  private Long limit;
  private Boolean desc;
  private List<String> fields;
  private List<String> outputFields;
  private List<String> omit;
  private Boolean skipEmpty;
  private Boolean skipHidden;
  private Boolean clean;
  private List<String> unwind;
  private List<String> flatten;
  private String view;
  private Boolean simplified;
  private Boolean skipFailedPages;
  private String signature;

  /** Number of items to skip. */
  public DatasetListItemsOptions offset(Long offset) {
    this.offset = offset;
    return this;
  }

  /**
   * Maximum number of items to return. Sent verbatim by {@code listItems(...)} (so {@code 0}
   * returns zero items); in {@code iterateItems(...)} a non-positive/zero {@code limit} means "no
   * cap" (all).
   */
  public DatasetListItemsOptions limit(Long limit) {
    this.limit = limit;
    return this;
  }

  /** Return items newest-first. */
  public DatasetListItemsOptions desc(Boolean desc) {
    this.desc = desc;
    return this;
  }

  /** Restrict the output to these fields. */
  public DatasetListItemsOptions fields(List<String> fields) {
    this.fields = fields == null ? null : List.copyOf(fields);
    return this;
  }

  /**
   * Positionally rename the fields selected by {@code fields} in the output (requires {@code
   * fields}).
   */
  public DatasetListItemsOptions outputFields(List<String> outputFields) {
    this.outputFields = outputFields == null ? null : List.copyOf(outputFields);
    return this;
  }

  /** Exclude these fields from the output. */
  public DatasetListItemsOptions omit(List<String> omit) {
    this.omit = omit == null ? null : List.copyOf(omit);
    return this;
  }

  /** Skip empty items. */
  public DatasetListItemsOptions skipEmpty(Boolean skipEmpty) {
    this.skipEmpty = skipEmpty;
    return this;
  }

  /** Skip hidden fields (those starting with {@code "#"}). */
  public DatasetListItemsOptions skipHidden(Boolean skipHidden) {
    this.skipHidden = skipHidden;
    return this;
  }

  /** Return only clean (non-empty, non-hidden) items. */
  public DatasetListItemsOptions clean(Boolean clean) {
    this.clean = clean;
    return this;
  }

  /** Expand these fields (each array element becomes a separate item). */
  public DatasetListItemsOptions unwind(List<String> unwind) {
    this.unwind = unwind == null ? null : List.copyOf(unwind);
    return this;
  }

  /** Flatten these nested fields into dot-notation keys. */
  public DatasetListItemsOptions flatten(List<String> flatten) {
    this.flatten = flatten == null ? null : List.copyOf(flatten);
    return this;
  }

  /** Select a predefined dataset view for field selection. */
  public DatasetListItemsOptions view(String view) {
    this.view = view;
    return this;
  }

  /** Return simplified (flattened, cleaned) items. */
  public DatasetListItemsOptions simplified(Boolean simplified) {
    this.simplified = simplified;
    return this;
  }

  /** Skip items that come from failed pages. */
  public DatasetListItemsOptions skipFailedPages(Boolean skipFailedPages) {
    this.skipFailedPages = skipFailedPages;
    return this;
  }

  /** A pre-shared URL signature granting access without an API token. */
  public DatasetListItemsOptions signature(String signature) {
    this.signature = signature;
    return this;
  }

  Boolean descValue() {
    return desc;
  }

  Long offsetValue() {
    return offset;
  }

  Long limitValue() {
    return limit;
  }

  void apply(QueryParams q) {
    q.addLong("offset", offset).addLong("limit", limit);
    applyFilters(q);
  }

  /**
   * Applies every option except {@code offset}/{@code limit}, which the iterator drives per page.
   */
  void applyFilters(QueryParams q) {
    q.addBool("desc", desc)
        .addCsv("fields", fields)
        .addCsv("outputFields", outputFields)
        .addCsv("omit", omit)
        .addBool("skipEmpty", skipEmpty)
        .addBool("skipHidden", skipHidden)
        .addBool("clean", clean)
        .addCsv("unwind", unwind)
        .addCsv("flatten", flatten)
        .addString("view", view)
        .addBool("simplified", simplified)
        .addBool("skipFailedPages", skipFailedPages)
        .addString("signature", signature);
  }
}
