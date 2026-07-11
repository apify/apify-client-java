package com.apify.client;

/** Options for listing/iterating the Apify Store ({@code GET /v2/store}). */
public final class StoreListOptions {
  private Long offset;
  private Long limit;
  private String search;
  private String sortBy;
  private String category;
  private String username;
  private String pricingModel;
  private Boolean includeUnrunnableActors;
  private Boolean allowsAgenticUsers;
  private String responseFormat;

  /** Number of Actors to skip. */
  public StoreListOptions offset(Long offset) {
    this.offset = offset;
    return this;
  }

  /**
   * Maximum number of Actors to return. Sent verbatim by {@code list(...)} (so {@code 0} returns
   * zero Actors); in {@code iterate(...)} a non-positive/zero {@code limit} means "no cap" (all).
   */
  public StoreListOptions limit(Long limit) {
    this.limit = limit;
    return this;
  }

  /** Full-text search query. */
  public StoreListOptions search(String search) {
    this.search = search;
    return this;
  }

  /** The sort field (e.g. {@code "popularity"}, {@code "newest"}). */
  public StoreListOptions sortBy(String sortBy) {
    this.sortBy = sortBy;
    return this;
  }

  /** Filter Actors by category. */
  public StoreListOptions category(String category) {
    this.category = category;
    return this;
  }

  /** Filter Actors by owner username. */
  public StoreListOptions username(String username) {
    this.username = username;
    return this;
  }

  /**
   * Filter Actors by pricing model ({@code FREE}, {@code FLAT_PRICE_PER_MONTH}, {@code
   * PRICE_PER_DATASET_ITEM}, {@code PAY_PER_EVENT}).
   */
  public StoreListOptions pricingModel(String pricingModel) {
    this.pricingModel = pricingModel;
    return this;
  }

  /** Include Actors the current user cannot run. */
  public StoreListOptions includeUnrunnableActors(Boolean includeUnrunnableActors) {
    this.includeUnrunnableActors = includeUnrunnableActors;
    return this;
  }

  /** Filter to Actors that allow agentic users. */
  public StoreListOptions allowsAgenticUsers(Boolean allowsAgenticUsers) {
    this.allowsAgenticUsers = allowsAgenticUsers;
    return this;
  }

  /** The response format ({@code full}, {@code agent}). */
  public StoreListOptions responseFormat(String responseFormat) {
    this.responseFormat = responseFormat;
    return this;
  }

  Long limitValue() {
    return limit;
  }

  Long offsetValue() {
    return offset;
  }

  void apply(QueryParams q) {
    q.addLong("offset", offset).addLong("limit", limit);
    applyFilters(q);
  }

  /**
   * Applies every filter except {@code offset}/{@code limit}, which the iterator drives per page.
   */
  void applyFilters(QueryParams q) {
    q.addString("search", search)
        .addString("sortBy", sortBy)
        .addString("category", category)
        .addString("username", username)
        .addString("pricingModel", pricingModel)
        .addBool("includeUnrunnableActors", includeUnrunnableActors)
        .addBool("allowsAgenticUsers", allowsAgenticUsers)
        .addString("responseFormat", responseFormat);
  }
}
