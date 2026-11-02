package com.apify.client.run;

import com.apify.client.ApifyResource;

/** The Server Actor (Actor Standby) deployment that served an {@link ActorRun}. */
public final class ActorRunStandby extends ApifyResource {
  private String deployment;

  /**
   * Which pool the run serves: {@code SINGLE_TENANT} is a private instance in the caller's account,
   * while the {@code MULTI_TENANT_FREE_USERS}, {@code MULTI_TENANT_PAYING_USERS} and {@code
   * MULTI_TENANT_OWNER_PAID} pools run in the Actor owner's account and serve many callers (charges
   * in them require a {@link RunChargeOptions#requestId(String) request ID}).
   */
  public String getDeployment() {
    return deployment;
  }
}
