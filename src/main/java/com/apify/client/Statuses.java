package com.apify.client;

import java.util.Set;

/** Run/build status helpers. Internal to the client. */
final class Statuses {

  /** Terminal run/build statuses: a resource in any of these is finished and will not change. */
  private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "FAILED", "ABORTED", "TIMED-OUT");

  private Statuses() {}

  /** Reports whether the status is a terminal (finished) run/build status. */
  static boolean isTerminal(String status) {
    return status != null && TERMINAL.contains(status);
  }
}
