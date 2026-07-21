package com.apify.client.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A Store Actor's current pricing model summary. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PricingInfo {
  private String pricingModel;

  /**
   * The pricing model, e.g. {@code "FREE"}, {@code "FLAT_PRICE_PER_MONTH"}, {@code
   * "PAY_PER_EVENT"}.
   */
  public String getPricingModel() {
    return pricingModel;
  }
}
