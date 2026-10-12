package com.apify.client.log;

import com.apify.client.http.ApifyApiException;
import com.apify.client.internal.Async;
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
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for redirecting a run's (or build's) streamed log to another destination, mirroring the
 * reference client's {@code StreamedLog}.
 *
 * <p>It follows the live raw log stream in the background and forwards each complete, timestamped
 * log message to a destination {@link Consumer} (by default a per-run prefixed SLF4J {@link
 * Logger}). Messages are parsed on log-line boundaries (the Apify platform prefixes every line with
 * an ISO-8601 timestamp), so multi-line messages are kept intact across stream chunks.
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

  /**
   * How long {@link #stop()}/{@link #close()} wait for a message already in flight before
   * force-closing the stream, matching the reference client's {@code STOP_GRACE_MILLIS}.
   */
  private static final long STOP_GRACE_MILLIS = 1_000;

  private final LogClient logClient;
  private final Consumer<String> destination;

  /**
   * Messages older than this instant are dropped ({@code null} means "redirect from the start", so
   * nothing is dropped). Set to construction time when {@code fromStart} is {@code false}.
   */
  private final Instant relevancyTimeLimit;

  private volatile boolean stopLogging;

  /**
   * Set once a destination consumer throws, so redirection unwinds on the first failure and
   * forwards nothing further (matching the reference client). Reset per {@link #start()}.
   */
  private volatile boolean forwardingFailed;

  private volatile InputStream activeStream;
  private Thread streamingThread;

  /**
   * Set for the duration between {@link #start()} being called and the async stream-open completing
   * (success or failure), closing the window where a concurrent {@link #start()} call would
   * otherwise see {@code streamingThread == null} and race the first call's setup.
   */
  private boolean starting;

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
    return logClient.stream(new LogOptions().raw(true))
        .handle(
            (stream, error) -> {
              synchronized (StreamedLog.this) {
                starting = false;
                if (error != null) {
                  Throwable cause = HttpClientCore.unwrapCompletion(error);
                  if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                  }
                  throw new IllegalStateException("failed to open log stream", cause);
                }
                activeStream = stream;
                Thread thread = new Thread(this::streamLog, "apify-streamed-log");
                thread.setDaemon(true);
                streamingThread = thread;
                thread.start();
              }
              return null;
            });
  }

  /**
   * Stops log redirection, waiting for the background reader thread to finish.
   *
   * <p>Called from any other thread (the normal case) this joins the reader before returning.
   * Called from <em>inside</em> the destination consumer - which runs on the reader thread - it
   * cannot join itself, so it only signals the stop and returns immediately; redirection then
   * unwinds as the reader returns from the consumer. For stopping from inside the consumer prefer
   * {@link #close()}: the reader's final flush can re-invoke the consumer for the last buffered
   * message, and a second {@code stop()} on the by-then-stopped helper throws, whereas {@code
   * close()} is idempotent.
   *
   * @throws IllegalStateException if redirection is not running - including a repeat call from
   *     within the consumer after an initial self-stop has already cleared the running thread
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
   * Signals the reader to stop and joins it (unless called from the reader thread itself, which
   * cannot join itself). Callers must hold the monitor and must have already checked that a thread
   * is running.
   */
  private void stopStreaming() {
    stopLogging = true;
    // Grace window before force-closing the live stream, so a message already in flight (e.g. the
    // run's last line, sent right as it finishes) still has a chance to arrive and be processed
    // instead of being cut off - matching the reference client's STOP_GRACE_MILLIS. A blocked read
    // either completes normally within this window or is unblocked by the forced close below.
    InputStream streamToForceClose = activeStream;
    CompletableFuture<Void> graceTimer =
        Async.delay(Duration.ofMillis(STOP_GRACE_MILLIS))
            .thenRun(() -> closeQuietly(streamToForceClose));
    // Wakes the reader if it is currently backing off before reopening an empty log stream (a plain
    // Thread.sleep - see awaitNonEmptyStream), which neither the stream close above nor the grace
    // window otherwise interrupts. It is a wake signal only, not a cancellation the reader must
    // honor: sleepAsWakeableBackoff clears it, matching its own javadoc.
    streamingThread.interrupt();
    // A thread must never join itself. The destination consumer runs on the streaming thread, so a
    // user calling stop()/close() from inside that consumer (e.g. "stop redirecting once I see line
    // X") would otherwise join() the current thread on itself and block forever - and because
    // stop()/close() are synchronized, that hung thread keeps the monitor, deadlocking every later
    // start/stop/close with no exception. Setting stopLogging already unwinds the reader loop
    // cooperatively (no read is blocked from inside the consumer's own call stack), so in that
    // self-stop case we skip only the join; the field is still cleared below so lifecycle state
    // stays consistent.
    if (Thread.currentThread() != streamingThread) {
      try {
        streamingThread.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    // The reader has exited (or, in the self-stop case, will unwind on its own without needing the
    // forced close) - cancelling here is what makes the grace window end early instead of always
    // costing the full STOP_GRACE_MILLIS, and prevents a late timer firing from closing a stream a
    // subsequent start() may already have replaced.
    graceTimer.cancel(false);
    streamingThread = null;
  }

  /** Reads the live raw log stream and forwards complete messages to the destination. */
  private void streamLog() {
    // Bytes after the last newline: an incomplete trailing line kept for the next read so a message
    // split across chunk boundaries (including a partial UTF-8 sequence) is not corrupted. Declared
    // outside the try so the `finally` block below can still emit it during the final flush.
    byte[] lineRemainder = new byte[0];
    // Decoded log text seen so far but not yet split into complete messages. Local to this reader
    // (not a shared field) so that if a start() ever races a still-draining reader, the two readers
    // never mutate the same non-thread-safe buffer.
    StringBuilder pending = new StringBuilder();
    InputStream stream;
    try {
      // The API serves the log of a run that has not logged anything yet as a stream that ends at
      // once; reopen it until it actually delivers a byte, so redirection does not silently give up
      // on a run that is still queued or starting.
      stream = awaitNonEmptyStream();
    } catch (IOException e) {
      if (!stopLogging) {
        LOG.warn("Log redirection stopped due to error", e);
      }
      return;
    }
    if (stream == null) {
      // Stopped before any stream ever delivered a byte: fetch whatever the log holds right now in
      // one plain request instead of redirecting nothing at all. Clears a pending interrupt status
      // first - stopStreaming()'s wake-up interrupt (see its javadoc) may not yet have been
      // consumed
      // by a Thread.sleep if it landed between two awaitNonEmptyStream attempts, and it must not
      // leak
      // into the destination consumer invoked below or into the blocking HTTP call just below that.
      Thread.interrupted();
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
    // The stream was opened (possibly reopened by awaitNonEmptyStream) and stored in activeStream;
    // own its lifecycle here via try-with-resources so the reader closes it when it exits
    // (idempotent
    // with stop()'s close, which may close it first to unblock a pending read).
    try (InputStream liveStream = stream) {
      byte[] readBuffer = new byte[READ_BUFFER_BYTES];
      int read;
      // Process each chunk before honouring a stop request, so a stop issued right after start
      // still redirects whatever the run has already produced (matching the reference client).
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
      // A read error after an explicit stop is expected (the stream was closed under us). Surface
      // only genuine, unsolicited failures, and do so without throwing from the background thread.
      if (!stopLogging) {
        LOG.warn("Log redirection stopped due to error", e);
      }
    } finally {
      // Clears a pending interrupt status before the flush below can invoke the destination
      // consumer: stopStreaming()'s wake-up interrupt (see its javadoc) is only guaranteed consumed
      // when it lands during awaitNonEmptyStream's Thread.sleep; landing instead while this loop
      // was
      // running would otherwise leak it into user code.
      Thread.interrupted();
      // Flush whatever is left when the stream ends OR when a stop closes the stream mid-read: the
      // last complete message is held back in `pending` by emitMessages(..., false), and a stop
      // unblocks the read via an IOException that skips the loop body. Running the final flush in
      // finally guarantees that retained message (and any unterminated trailing line) is still
      // delivered on stop, matching the reference client. Skipped once a consumer has thrown: there
      // is nothing more to deliver and the flush must not re-invoke the failed consumer.
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
      // Always attempt the read before consulting stopLogging - like the main loop below, which
      // processes a chunk before honouring a stop request - so a stop issued right after start()
      // still drains whatever is already buffered (e.g. a finite stream in tests) instead of
      // discarding it.
      byte[] probe = new byte[READ_BUFFER_BYTES];
      int read;
      try {
        read = stream.read(probe);
      } catch (IOException e) {
        // An explicit stop can close `stream` out from under this read; that is expected, not a
        // genuine failure (mirrors the main loop's own IOException handling below).
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
      // A plain volatile write (no lock needed for a single field): stop()/close() read this same
      // field to decide what to force-close, and a `synchronized` publish here could instead
      // deadlock against a concurrent stop()/close() - both are `synchronized` methods that may be
      // blocked in streamingThread.join() while holding that monitor, which this reader thread
      // could
      // never then acquire.
      activeStream = stream;
      // A stop() requested while the reopen above was in flight force-closed the *previous* stream
      // (the one this method already closed on seeing it empty), not this newly opened one - so it
      // would otherwise be handed to the caller with nothing left able to unblock a read that then
      // blocks. Closing it here too keeps every reopened stream subject to the same stop signal.
      if (stopLogging) {
        closeQuietly(stream);
        return null;
      }
    }
  }

  /**
   * Sleeps for {@code millis}, waking early if interrupted and clearing (rather than re-raising)
   * the interrupt status - {@link #stopStreaming()} uses interruption purely to wake this wait
   * promptly; {@link #stopLogging}, checked by the caller right after, is the actual stop signal,
   * so a lingering interrupt status would only risk breaking a later, unrelated blocking call.
   */
  private static void sleepAsWakeableBackoff(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ignored) {
      // Intentionally not restoring the interrupt status - see method javadoc.
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
      // The destination is a user-supplied consumer running on this background daemon thread; an
      // uncaught throw would kill the thread. Match the reference client: stop redirecting on the
      // first failure (forwardingFailed unwinds the emit loop and skips the final flush, so the
      // consumer is not invoked again) and log a single warning instead of propagating.
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
