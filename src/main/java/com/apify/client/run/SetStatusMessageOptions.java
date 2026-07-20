package com.apify.client.run;

/**
 * Configures {@code com.apify.client.ApifyClient#setStatusMessage(String,
 * SetStatusMessageOptions)}.
 */
public final class SetStatusMessageOptions {

  private Boolean statusMessageTerminal;

  /**
   * If {@code true}, marks the message as final so it won't be overwritten by a subsequent
   * non-terminal status message. Leave unset to let the server apply its default.
   */
  public SetStatusMessageOptions isStatusMessageTerminal(boolean isStatusMessageTerminal) {
    this.statusMessageTerminal = isStatusMessageTerminal;
    return this;
  }

  /** Whether the message is marked terminal, or {@code null} if unset. */
  public Boolean isStatusMessageTerminal() {
    return statusMessageTerminal;
  }
}
