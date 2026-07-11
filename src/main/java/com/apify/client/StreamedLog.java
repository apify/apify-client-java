package com.apify.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper for redirecting a run's (or build's) streamed log to another destination, mirroring the
 * reference client's {@code StreamedLog}.
 *
 * <p>It follows the live raw log stream in the background and forwards each complete, timestamped
 * log message to a destination {@link Consumer} (by default a per-run prefixed {@link Logger}).
 * Messages are parsed on log-line boundaries (the Apify platform prefixes every line with an
 * ISO-8601 timestamp), so multi-line messages are kept intact across stream chunks.
 *
 * <p>Lifecycle: call {@link #start()} to begin redirection and {@link #stop()} to end it. The class
 * is {@link AutoCloseable}, so it can be used in a try-with-resources block; {@link #close()} stops
 * redirection if it is still running.
 *
 * <p>Typical use:
 *
 * <pre>{@code
 * RunClient runClient = client.run(runId);
 * try (StreamedLog streamedLog = runClient.getStreamedLog()) {
 *   streamedLog.start();
 *   runClient.waitForFinish(120L);
 * }
 * }</pre>
 */
public final class StreamedLog implements AutoCloseable {

  /**
   * Marks the start of a log message: an ISO-8601 timestamp at the beginning of a line (or of the
   * stream). The platform prefixes every log line with such a timestamp, so this reliably splits
   * concatenated messages while keeping multi-line messages together.
   */
  private static final Pattern MESSAGE_MARKER =
      Pattern.compile("(?:\\n|^)(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z)");

  /** Read buffer size for pulling bytes off the live log stream. */
  private static final int READ_BUFFER_BYTES = 8192;

  private final LogClient logClient;
  private final Consumer<String> destination;

  /**
   * Messages older than this instant are dropped ({@code null} means "redirect from the start", so
   * nothing is dropped). Set to construction time when {@code fromStart} is {@code false}.
   */
  private final Instant relevancyTimeLimit;

  /** Decoded log text seen so far but not yet split into complete messages. */
  private final StringBuilder pending = new StringBuilder();

  private volatile boolean stopLogging;
  private volatile InputStream activeStream;
  private Thread streamingThread;

  StreamedLog(LogClient logClient, Consumer<String> destination, boolean fromStart) {
    this.logClient = logClient;
    this.destination = destination;
    this.relevancyTimeLimit = fromStart ? null : Instant.now();
  }

  /**
   * Starts redirecting the log in a background daemon thread.
   *
   * <p>The live log stream is opened synchronously before the thread is launched, so this call
   * blocks briefly on the HTTP round-trip and surfaces a failure to open the stream to the caller
   * rather than on the background thread.
   *
   * @throws IllegalStateException if redirection is already running
   * @throws ApifyApiException if the log stream cannot be opened (the API returns a non-2xx
   *     status); other transport failures propagate as their own runtime exception
   */
  public synchronized void start() {
    if (streamingThread != null) {
      throw new IllegalStateException("Streaming task already active");
    }
    stopLogging = false;
    pending.setLength(0);
    // Open the live stream here, before launching the reader thread, so activeStream is guaranteed
    // to be set once the thread exists. If it were opened inside the thread, a stop() that ran
    // during the HTTP round-trip would close a still-null stream, leaving the reader blocked on a
    // live read() that never returns and join() hanging forever. Opening it up front also lets a
    // failed connection surface to the caller of start() instead of a background-thread warning.
    activeStream = logClient.stream(new LogOptions().raw(true));
    Thread thread = new Thread(this::streamLog, "apify-streamed-log");
    thread.setDaemon(true);
    streamingThread = thread;
    thread.start();
  }

  /**
   * Stops log redirection and waits for the background thread to finish.
   *
   * @throws IllegalStateException if redirection is not running
   */
  public synchronized void stop() {
    if (streamingThread == null) {
      throw new IllegalStateException("Streaming task is not active");
    }
    stopStreaming();
  }

  /**
   * Stops redirection if it is still running; a no-op otherwise. Fully idempotent and safe under a
   * concurrent {@link #stop()} or a double {@code close()}: the running-check and the stop happen
   * atomically under the monitor, so unlike {@link #stop()} this never throws.
   */
  @Override
  public synchronized void close() {
    if (streamingThread != null) {
      stopStreaming();
    }
  }

  /**
   * Signals the reader to stop, unblocks it by closing the live stream, and joins it (unless called
   * from the reader thread itself, which cannot join itself). Callers must hold the monitor and
   * must have already checked that a thread is running.
   */
  private void stopStreaming() {
    stopLogging = true;
    // Close the live stream so a blocked read returns promptly instead of waiting for more bytes.
    closeQuietly(activeStream);
    // A thread must never join itself. The destination consumer runs on the streaming thread, so a
    // user calling stop()/close() from inside that consumer (e.g. "stop redirecting once I see line
    // X") would otherwise join() the current thread on itself and block forever - and because
    // stop()/close() are synchronized, that hung thread keeps the monitor, deadlocking every later
    // start/stop/close with no exception. Setting stopLogging + closing the stream already unwinds
    // the reader loop, so in that self-stop case we skip only the join; the field is still cleared
    // below so lifecycle state stays consistent.
    if (Thread.currentThread() != streamingThread) {
      try {
        streamingThread.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    streamingThread = null;
  }

  /** Reads the live raw log stream and forwards complete messages to the destination. */
  private void streamLog() {
    // Bytes after the last newline: an incomplete trailing line kept for the next read so a message
    // split across chunk boundaries (including a partial UTF-8 sequence) is not corrupted. Declared
    // outside the try so the final flush in finally can still emit it (see below).
    byte[] lineRemainder = new byte[0];
    // The stream was opened in start() and stored in activeStream; own its lifecycle here via
    // try-with-resources so the reader closes it when it exits (idempotent with stop()'s close,
    // which may close it first to unblock a pending read).
    try (InputStream stream = activeStream) {
      byte[] readBuffer = new byte[READ_BUFFER_BYTES];
      int read;
      // Process each chunk before honouring a stop request, so a stop issued right after start
      // still redirects whatever the run has already produced (matching the reference client).
      while ((read = stream.read(readBuffer)) != -1) {
        byte[] combined = new byte[lineRemainder.length + read];
        System.arraycopy(lineRemainder, 0, combined, 0, lineRemainder.length);
        System.arraycopy(readBuffer, 0, combined, lineRemainder.length, read);

        int lastNewline = lastIndexOf(combined, (byte) '\n');
        if (lastNewline >= 0) {
          String completeText = new String(combined, 0, lastNewline + 1, StandardCharsets.UTF_8);
          lineRemainder = new byte[combined.length - (lastNewline + 1)];
          System.arraycopy(combined, lastNewline + 1, lineRemainder, 0, lineRemainder.length);
          emitMessages(completeText, false);
        } else {
          lineRemainder = combined;
        }
        if (stopLogging) {
          break;
        }
      }
    } catch (IOException e) {
      // A read error after an explicit stop is expected (the stream was closed under us). Surface
      // only genuine, unsolicited failures, and do so without throwing from the background thread.
      if (!stopLogging) {
        Logger.getLogger(StreamedLog.class.getName())
            .log(Level.WARNING, "Log redirection stopped due to error", e);
      }
    } finally {
      // Flush whatever is left when the stream ends OR when a stop closes the stream mid-read: the
      // last complete message is held back in `pending` by emitMessages(..., false), and a stop
      // unblocks the read via an IOException that skips the loop body. Running the final flush in
      // finally guarantees that retained message (and any unterminated trailing line) is still
      // delivered on stop, matching the reference client.
      emitMessages(new String(lineRemainder, StandardCharsets.UTF_8), true);
    }
  }

  /**
   * Appends {@code text} to the pending buffer, then forwards every complete message it contains.
   * When {@code flush} is {@code false} the last message is held back (it may still be growing);
   * when {@code true} everything remaining is emitted.
   */
  private void emitMessages(String text, boolean flush) {
    pending.append(text);
    String buffered = pending.toString();

    Matcher matcher = MESSAGE_MARKER.matcher(buffered);
    List<Integer> messageStarts = new ArrayList<>();
    List<String> timestamps = new ArrayList<>();
    while (matcher.find()) {
      messageStarts.add(matcher.start(1));
      timestamps.add(matcher.group(1));
    }

    if (messageStarts.isEmpty()) {
      // No complete message yet; keep buffering unless this is the final flush.
      if (flush) {
        emitIfRelevant(buffered.trim(), null);
        pending.setLength(0);
      }
      return;
    }

    int completeCount = flush ? messageStarts.size() : messageStarts.size() - 1;
    for (int i = 0; i < completeCount; i++) {
      int from = messageStarts.get(i);
      int to = (i + 1 < messageStarts.size()) ? messageStarts.get(i + 1) : buffered.length();
      emitIfRelevant(buffered.substring(from, to).trim(), timestamps.get(i));
    }

    if (flush) {
      pending.setLength(0);
    } else {
      // Retain the last (possibly still-growing) message for the next round.
      String rest = buffered.substring(messageStarts.get(messageStarts.size() - 1));
      pending.setLength(0);
      pending.append(rest);
    }
  }

  /**
   * Forwards a trimmed message to the destination, dropping it if it is empty or (when a relevancy
   * limit is set) older than that limit.
   */
  private void emitIfRelevant(String message, String timestamp) {
    if (message.isEmpty()) {
      return;
    }
    if (relevancyTimeLimit != null && timestamp != null) {
      try {
        if (Instant.parse(timestamp).isBefore(relevancyTimeLimit)) {
          return;
        }
      } catch (DateTimeParseException ignored) {
        // Unparseable timestamp: keep the message rather than silently dropping log output.
      }
    }
    destination.accept(message);
  }

  private static int lastIndexOf(byte[] bytes, byte target) {
    for (int i = bytes.length - 1; i >= 0; i--) {
      if (bytes[i] == target) {
        return i;
      }
    }
    return -1;
  }

  private static void closeQuietly(InputStream stream) {
    if (stream == null) {
      return;
    }
    try {
      stream.close();
    } catch (IOException ignored) {
      // Best-effort close; nothing actionable if it fails.
    }
  }
}
