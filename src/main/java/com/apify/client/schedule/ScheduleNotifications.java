package com.apify.client.schedule;

import com.apify.client.ApifyResource;

/** Notification settings for a {@link Schedule}. */
public final class ScheduleNotifications extends ApifyResource {
  private boolean email;

  /** Whether the schedule owner is notified by email of run failures. */
  public boolean isEmail() {
    return email;
  }
}
