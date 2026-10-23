package com.apify.client.store;

import com.apify.client.ApifyResource;

/** A Store Actor's current pricing model summary. */
public final class PricingInfo extends ApifyResource {
  private String pricingModel;

  /**
   * The pricing model, e.g. {@code "FREE"}, {@code "PRICE_PER_DATASET_ITEM"}, {@code
   * "PAY_PER_EVENT"}.
   */
  public String getPricingModel() {
    return pricingModel;
  }
}
