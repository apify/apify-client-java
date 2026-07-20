package com.apify.client.schedule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Notification settings for a {@link Schedule}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ScheduleNotifications {
  private boolean email;

  /** Whether the schedule owner is notified by email of run failures. */
  public boolean isEmail() {
    return email;
  }
}
