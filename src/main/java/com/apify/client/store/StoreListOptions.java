package com.apify.client.store;

import com.apify.client.internal.ListOptionsLike;
import com.apify.client.internal.QueryParams;

/** Options for listing/iterating the Apify Store ({@code GET /v2/store}). */
public final class StoreListOptions implements ListOptionsLike {
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
  private Boolean includeInputSchema;

  /** Number of Actors to skip. The API ignores it when {@link #search(String)} is used. */
  public StoreListOptions offset(Long offset) {
    this.offset = offset;
    return this;
  }

  /**
   * Maximum number of Actors to return. The API default and maximum is {@code 1000}; when {@link
   * #search(String)} is used, both are {@code 100} and a larger value is reduced to {@code 100}.
   * Sent verbatim by {@code list(...)} (so {@code 0} returns zero Actors); in {@code iterate(...)}
   * a non-positive/zero {@code limit} means "no cap" (all).
   */
  public StoreListOptions limit(Long limit) {
    this.limit = limit;
    return this;
  }

  /**
   * Search query. The API ranks the results by relevance and returns them as a single page of at
   * most 100 Actors: {@link #offset(Long)} is ignored and there is no second page. Narrow the
   * results with {@link #category(String)}, {@link #username(String)}, or {@link
   * #pricingModel(String)}.
   */
  public StoreListOptions search(String search) {
    this.search = search;
    return this;
  }

  /**
   * Accepted for backward compatibility, but the API ignores it. The results are ordered by
   * relevance when {@link #search(String)} is used and by Store position otherwise; sort them on
   * the client for a different order.
   *
   * @deprecated the API ignores the value
   */
  @Deprecated
  public StoreListOptions sortBy(String sortBy) {
    this.sortBy = sortBy;
    return this;
  }

  /**
   * Filter Actors by category. Accepts one or more category IDs (e.g. {@code "AI_AND_ML"}),
   * separated by commas and case-insensitive; an Actor must match all of them. Legacy keys such as
   * {@code social_media} are still accepted, and an unknown category fails with a {@code 400}
   * error.
   */
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
   * Filter Actors by pricing model ({@code FREE}, {@code PRICE_PER_DATASET_ITEM}, {@code
   * PAY_PER_EVENT}). The rental model ({@code FLAT_PRICE_PER_MONTH}) is deprecated and no longer
   * supported, so it is no longer documented as a filter value and matches no Actors.
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

  /**
   * Include a compact {@code inputSchema} (property types and required properties only) of each
   * Actor's default build in the results, available through {@code ActorStoreListItem#getExtra()}.
   * With this flag the API defaults {@code limit} to, and caps it at, {@code 10}.
   */
  public StoreListOptions includeInputSchema(Boolean includeInputSchema) {
    this.includeInputSchema = includeInputSchema;
    return this;
  }

  /** Whether a non-empty search query is set, which makes the API return a single page. */
  boolean hasSearch() {
    return search != null && !search.isEmpty();
  }

  @Override
  public Long limitValue() {
    return limit;
  }

  @Override
  public Long offsetValue() {
    return offset;
  }

  @Override
  public void apply(QueryParams q) {
    q.addLong("offset", offset).addLong("limit", limit);
    applyFilters(q);
  }

  /**
   * Applies every filter except {@code offset}/{@code limit}, which the iterator drives per page.
   */
  @Override
  public void applyFilters(QueryParams q) {
    q.addString("search", search)
        .addString("sortBy", sortBy)
        .addString("category", category)
        .addString("username", username)
        .addString("pricingModel", pricingModel)
        .addBool("includeUnrunnableActors", includeUnrunnableActors)
        .addBool("allowsAgenticUsers", allowsAgenticUsers)
        .addString("responseFormat", responseFormat)
        .addBool("includeInputSchema", includeInputSchema);
  }
}
