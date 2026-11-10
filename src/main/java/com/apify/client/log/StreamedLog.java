package com.apify.client.log;

import com.apify.client.http.ApifyApiException;
import com.apify.client.internal.Async;
import com.apify.client.internal.CancellableFuture;
import com.apify.client.internal.HttpClientCore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redirects a run's or build's streamed log to another destination.
 *
 * <p>It follows the live log stream in the background and forwards each complete log message to a
 * {@link Consumer} (by default a per-run prefixed SLF4J {@link Logger}). Messages start at a line
 * with an ISO-8601 timestamp, so multi-line messages stay intact.
 *
 * <p>Call {@link #start()} to begin and {@link #stop()} to end. {@link #close()} stops redirection
 * if it is still running, so the class works in try-with-resources.
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

  /** SLF4J logger for this class's own diagnostics (distinct from the redirected run/build log). */
  private static final Logger LOG = LoggerFactory.getLogger(StreamedLog.class);

  /**
   * Marks the start of a log message: an ISO-8601 timestamp at the beginning of a line (or of the
   * stream). The platform prefixes every log line with such a timestamp, so this reliably splits
   * concatenated messages while keeping multi-line messages together.
   */
  private static final Pattern MESSAGE_MARKER =
      Pattern.compile("(?:\\n|^)(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z)");

  /** Read buffer size for pulling bytes off the live log stream. */
  private static final int READ_BUFFER_BYTES = 8192;

  /**
   * Pause before the first reopen of a log stream that ended before the run had logged anything
   * (the API serves that case as a stream that ends at once). Doubles on each further reopen, up to
   * {@link #EMPTY_STREAM_MAX_RETRY_MILLIS}, which bounds the request rate while a run takes a long
   * time to start producing output (e.g. while queued for resources).
   */
  private static final long EMPTY_STREAM_RETRY_MILLIS = 500;

  /** Upper bound on the pause between reopens of an empty log stream. */
  private static final long EMPTY_STREAM_MAX_RETRY_MILLIS = 5_000;

  /** How long {@link #stop()} and {@link #close()} wait for a message in flight before closing. */
  private static final long STOP_GRACE_MILLIS = 1_000;

  private final LogClient logClient;
  private final Consumer<String> destination;

  /** Messages older than this are dropped; {@code null} drops nothing. */
  private final Instant relevancyTimeLimit;

  private volatile boolean stopLogging;

  /** Set when the destination consumer throws; nothing is forwarded after that. */
  private volatile boolean forwardingFailed;

  private volatile InputStream activeStream;
  private Thread streamingThread;

  /**
   * Set for the duration between {@link #start()} being called and the async stream-open completing
   * (success or failure), closing the window where a concurrent {@link #start()} call would
   * otherwise see {@code streamingThread == null} and race the first call's setup.
   */
  private boolean starting;

  /**
   * Set by {@link #close()} while a {@link #start()} is still opening its stream: the stream is
   * then closed as soon as it opens, and no reader thread is launched.
   */
  private boolean closeRequested;

  public StreamedLog(LogClient logClient, Consumer<String> destination, boolean fromStart) {
    this.logClient = logClient;
    this.destination = destination;
    this.relevancyTimeLimit = fromStart ? null : Instant.now();
  }

  /**
   * Starts redirecting the log in a background daemon thread.
   *
   * <p>Opening the live log stream is asynchronous (this call returns immediately); the returned
   * future completes once the stream is open and the background reader thread has been launched, or
   * exceptionally if the stream could not be opened. If it were opened inside the reader thread
   * instead, a {@link #stop()} that ran during the HTTP round-trip would close a still-null stream,
   * leaving the reader blocked on a live read that never returns and {@code join()} hanging
   * forever.
   *
   * <p>Cancelling the returned future cancels the stream-open request. {@link #close()} while the
   * stream is still opening closes the stream once it opens, launches no reader and cancels the
   * returned future.
   *
   * @throws IllegalStateException if redirection is already running (or a previous {@link #start()}
   *     call's stream-open is still in flight)
   * @implNote the returned future completes exceptionally with an {@link ApifyApiException} if the
   *     log stream cannot be opened (the API returns a non-2xx status); other transport failures
   *     complete it with their own runtime exception.
   */
  public synchronized CompletableFuture<Void> start() {
    if (streamingThread != null || starting) {
      throw new IllegalStateException("Streaming task already active");
    }
    starting = true;
    stopLogging = false;
    forwardingFailed = false;
    CancellableFuture<Void> started = new CancellableFuture<>();
    CompletableFuture<InputStream> opening = logClient.stream(new LogOptions().raw(true));
    // Cancelling the returned future cancels the stream-open request. The callback below hangs off
    // the open request itself, so it runs however that ends and always resets the opening state.
    started.link(opening);
    opening.whenComplete(
        (stream, error) -> {
          // The outcome is decided under the lock but delivered after it is released: the stages
          // chained on the returned future run on this thread, and one of them may wait for a
          // destination consumer that itself calls close() (which takes the lock).
          Runnable outcome;
          synchronized (StreamedLog.this) {
            starting = false;
            if (error != null) {
              closeRequested = false;
              Throwable cause = HttpClientCore.unwrapCompletion(error);
              Throwable failure =
                  cause instanceof RuntimeException
                      ? cause
                      : new IllegalStateException("failed to open log stream", cause);
              outcome = () -> started.completeExceptionally(failure);
            } else if (closeRequested || started.isCancelled()) {
              // Closed (or cancelled) while the stream was opening: do not launch a reader for it.
              closeRequested = false;
              outcome =
                  () -> {
                    closeQuietly(stream);
                    started.cancel(true);
                  };
            } else {
              activeStream = stream;
              Thread thread = new Thread(this::streamLog, "apify-streamed-log");
              thread.setDaemon(true);
              streamingThread = thread;
              thread.start();
              outcome = () -> started.complete(null);
            }
          }
          outcome.run();
        });
    return started;
  }

  /**
   * Stops log redirection, waiting for the background reader thread to finish.
   *
   * <p>Called from inside the destination consumer, which runs on the reader thread, it cannot join
   * itself: it only signals the stop and returns. If the calling thread is interrupted, it closes
   * the stream, keeps the interrupt flag and returns before the reader has ended, so the last
   * message may arrive after it returns. {@link #start()} throws until a later {@code stop()} or
   * {@link #close()} has joined the reader.
   *
   * @throws IllegalStateException if redirection is not running
   */
  public void stop() {
    StopHandle handle;
    synchronized (this) {
      if (streamingThread == null) {
        throw new IllegalStateException("Streaming task is not active");
      }
      handle = signalStop();
    }
    awaitStopped(handle);
  }

  /**
   * Stops redirection if it is still running; a no-op otherwise. If {@link #start()} is still
   * opening the log stream, the stream is closed as soon as it opens, no reader is launched and the
   * future {@code start()} returned is cancelled. Waits and handles interruption and a call from
   * the consumer as {@link #stop()} does. Idempotent and safe to call concurrently with {@code
   * stop()}; unlike it, never throws.
   */
  @Override
  public void close() {
    StopHandle handle = null;
    synchronized (this) {
      if (starting) {
        closeRequested = true;
      }
      if (streamingThread != null) {
        handle = signalStop();
      }
    }
    if (handle != null) {
      awaitStopped(handle);
    }
  }

  /**
   * The reader thread being stopped, its stream, and the timer that closes the stream after the
   * grace window.
   */
  private record StopHandle(
      Thread thread, InputStream stream, CompletableFuture<Void> graceTimer) {}

  /**
   * Signals the reader to stop. The caller holds the monitor and has checked that a thread is
   * running, and joins it with {@link #awaitStopped} after releasing the monitor: the consumer runs
   * on the reader thread and may call {@link #close()}, which needs the monitor.
   */
  private StopHandle signalStop() {
    stopLogging = true;
    // Grace window before closing the stream, so the run's last line still arrives.
    InputStream streamToForceClose = activeStream;
    CompletableFuture<Void> graceTimer =
        Async.delay(Duration.ofMillis(STOP_GRACE_MILLIS))
            .thenRun(() -> closeQuietly(streamToForceClose));
    // Wakes the reader if it is backing off before reopening an empty stream. Not an interrupt:
    // that would reach a consumer that is running or blocked in its own call.
    LockSupport.unpark(streamingThread);
    return new StopHandle(streamingThread, streamToForceClose, graceTimer);
  }

  /**
   * Joins the stopped reader and clears the state. A stop from the consumer, which runs on the
   * reader thread, neither joins nor clears. A join that is interrupted closes the stream, keeps
   * the interrupt flag and does not clear. In both cases {@link #start()} stays rejected until a
   * later {@code stop()} or {@code close()} has joined the reader.
   */
  private void awaitStopped(StopHandle handle) {
    if (Thread.currentThread() == handle.thread()) {
      return;
    }
    boolean joined = true;
    try {
      handle.thread().join();
    } catch (InterruptedException e) {
      joined = false;
      Thread.currentThread().interrupt();
      // The grace window cannot be waited out, so end the reader now instead of leaving it blocked.
      closeQuietly(handle.stream());
    }
    // Cancelling ends the grace window early and keeps a late timer from closing a stream that a
    // later start() opened.
    handle.graceTimer().cancel(false);
    if (joined) {
      synchronized (this) {
        if (streamingThread == handle.thread()) {
          streamingThread = null;
        }
      }
    }
  }

  /** Reads the live raw log stream and forwards complete messages to the destination. */
  private void streamLog() {
    // Bytes after the last newline, kept for the next read so that a message split across chunks,
    // even in the middle of a UTF-8 sequence, is not corrupted. Declared outside the try so the
    // final flush can emit it.
    byte[] lineRemainder = new byte[0];
    // Decoded text not yet split into complete messages. Local, so overlapping readers never share
    // it.
    StringBuilder pending = new StringBuilder();
    InputStream stream;
    try {
      // The API serves the log of a run that has not logged yet as a stream that ends at once.
      // Reopen it until a byte arrives, so a queued or starting run is not given up on.
      stream = awaitNonEmptyStream();
    } catch (IOException e) {
      if (!stopLogging) {
        LOG.warn("Log redirection stopped due to error", e);
      }
      return;
    }
    if (stream == null) {
      // Stopped before any byte arrived: fetch the log as it is now.
      try {
        String logContent = logClient.get(new LogOptions().raw(true)).join().orElse("");
        if (!forwardingFailed) {
          emitMessages(pending, logContent, true);
        }
      } catch (RuntimeException e) {
        LOG.warn("Log redirection stopped due to error", e);
      }
      return;
    }
    // The reader owns the stream and closes it on exit; stop() may close it first to end a read.
    try (InputStream liveStream = stream) {
      byte[] readBuffer = new byte[READ_BUFFER_BYTES];
      int read;
      // Process each chunk before honouring a stop request, so a stop issued right after start
      // still redirects whatever the run has already produced.
      while ((read = liveStream.read(readBuffer)) != -1) {
        byte[] combined = new byte[lineRemainder.length + read];
        System.arraycopy(lineRemainder, 0, combined, 0, lineRemainder.length);
        System.arraycopy(readBuffer, 0, combined, lineRemainder.length, read);

        int lastNewline = lastIndexOf(combined, (byte) '\n');
        if (lastNewline >= 0) {
          String completeText = new String(combined, 0, lastNewline + 1, StandardCharsets.UTF_8);
          lineRemainder = new byte[combined.length - (lastNewline + 1)];
          System.arraycopy(combined, lastNewline + 1, lineRemainder, 0, lineRemainder.length);
          emitMessages(pending, completeText, false);
        } else {
          lineRemainder = combined;
        }
        if (stopLogging) {
          break;
        }
      }
    } catch (IOException e) {
      // A read error after an explicit stop is expected. Report only unsolicited failures.
      if (!stopLogging) {
        LOG.warn("Log redirection stopped due to error", e);
      }
    } finally {
      // Flush on stream end and on a stop mid-read: emitMessages holds back the last message and a
      // stop skips the loop body. Skipped once a consumer has thrown.
      if (!forwardingFailed) {
        emitMessages(pending, new String(lineRemainder, StandardCharsets.UTF_8), true);
      }
    }
  }

  /**
   * Waits for the log stream to actually start delivering bytes, reopening it (pausing {@link
   * #EMPTY_STREAM_RETRY_MILLIS}, doubled on each further attempt) while it keeps ending with none.
   * Returns a stream with any bytes already consumed while probing it stitched back onto the front,
   * as soon as one delivers at least one byte; or {@code null} if {@link #stop()}/{@link #close()}
   * is requested before any stream ever delivers a byte.
   */
  private InputStream awaitNonEmptyStream() throws IOException {
    InputStream stream = activeStream;
    long backoffMillis = EMPTY_STREAM_RETRY_MILLIS;
    while (true) {
      // Read before checking stopLogging, so a stop right after start() still drains buffered data.
      byte[] probe = new byte[READ_BUFFER_BYTES];
      int read;
      try {
        read = stream.read(probe);
      } catch (IOException e) {
        // An explicit stop closes the stream under this read.
        if (stopLogging) {
          return null;
        }
        throw e;
      }
      if (read != -1) {
        return new SequenceInputStream(new ByteArrayInputStream(probe, 0, read), stream);
      }
      closeQuietly(stream);
      if (stopLogging) {
        return null;
      }
      sleepAsWakeableBackoff(backoffMillis);
      backoffMillis = Math.min(backoffMillis * 2, EMPTY_STREAM_MAX_RETRY_MILLIS);
      if (stopLogging) {
        return null;
      }
      try {
        stream = logClient.stream(new LogOptions().raw(true)).join();
      } catch (RuntimeException e) {
        if (stopLogging) {
          return null;
        }
        Throwable cause = HttpClientCore.unwrapCompletion(e);
        if (cause instanceof IOException ioException) {
          throw ioException;
        }
        throw new IOException("failed to reopen log stream", cause);
      }
      // A plain volatile write, so the reader never waits for the monitor here.
      activeStream = stream;
      // A stop during the reopen closed the previous stream, not this one, so close it here.
      if (stopLogging) {
        closeQuietly(stream);
        return null;
      }
    }
  }

  /**
   * Sleeps for {@code millis}, waking early when {@link #signalStop()} sets {@link #stopLogging}.
   */
  private void sleepAsWakeableBackoff(long millis) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
    long remaining = deadline - System.nanoTime();
    while (!stopLogging && remaining > 0) {
      LockSupport.parkNanos(this, remaining);
      remaining = deadline - System.nanoTime();
    }
  }

  /**
   * Appends {@code text} to the pending buffer, then forwards every complete message it contains.
   * When {@code flush} is {@code false} the last message is held back (it may still be growing);
   * when {@code true} everything remaining is emitted.
   */
  private void emitMessages(StringBuilder pending, String text, boolean flush) {
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
      if (forwardingFailed) {
        // A consumer threw: unwind on the first failure without forwarding the rest of this batch.
        return;
      }
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
    try {
      destination.accept(message);
    } catch (RuntimeException e) {
      // The consumer runs on the background thread: stop at the first failure and log one warning
      // instead of propagating.
      forwardingFailed = true;
      stopLogging = true;
      LOG.warn("Log redirection stopped due to error", e);
    }
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
