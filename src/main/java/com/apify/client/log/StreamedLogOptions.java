package com.apify.client.log;

import com.apify.client.run.RunClient;
import java.util.function.Consumer;

/**
 * Configures {@link RunClient#getStreamedLog(StreamedLogOptions)} log redirection.
 *
 * <p>Mirrors the reference client's {@code getStreamedLog} options: a destination log ({@code
 * toLog}) and whether to include messages produced before redirection started ({@code fromStart}).
 */
public final class StreamedLogOptions {

  private Consumer<String> toLog;
  private String prefix;
  private boolean fromStart = true;

  /**
   * Destination for redirected log messages. Each complete log message is passed to the consumer.
   * When left unset, messages go to an SLF4J {@code Logger} at {@code INFO} level with an
   * auto-built per-run prefix.
   */
  public StreamedLogOptions toLog(Consumer<String> toLog) {
    this.toLog = toLog;
    return this;
  }

  /**
   * Prefix prepended to each message by the default destination logger. Ignored when a custom
   * {@link #toLog(Consumer)} destination is set. When unset, a per-run prefix (Actor name and run
   * id) is built automatically.
   */
  public StreamedLogOptions prefix(String prefix) {
    this.prefix = prefix;
    return this;
  }

  /**
   * Whether to redirect all of the run's logs, including those produced before redirection started
   * ({@code true}, the default). When {@code false}, only messages timestamped at or after the
   * moment redirection is created are redirected.
   */
  public StreamedLogOptions fromStart(boolean fromStart) {
    this.fromStart = fromStart;
    return this;
  }

  public Consumer<String> destination() {
    return toLog;
  }

  public String prefixValue() {
    return prefix;
  }

  public boolean fromStartValue() {
    return fromStart;
  }
}
