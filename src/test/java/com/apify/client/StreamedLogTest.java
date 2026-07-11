package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

/**
 * Offline tests for the {@link StreamedLog} log-redirection helper: it must forward complete
 * timestamped messages to the destination, drop messages older than the relevancy limit when {@code
 * fromStart} is false, flush a final unterminated line, and enforce its start/stop lifecycle.
 */
class StreamedLogTest {

  private static ApifyClient client(MockBackend backend) {
    return ApifyClient.builder().token("test-token").httpBackend(backend).maxRetries(0).build();
  }

  /** Drives a run's log stream from a scripted body and collects redirected messages. */
  private static List<String> redirect(String streamBody, StreamedLogOptions options)
      throws InterruptedException {
    MockBackend backend = MockBackend.ofConstant(200, "");
    backend.scriptStream(200, streamBody);
    List<String> collected = new CopyOnWriteArrayList<>();
    StreamedLog streamedLog =
        client(backend).run("run123").getStreamedLog(options.toLog(collected::add));
    streamedLog.start();
    // The scripted stream is finite, so redirection finishes on its own; stop() joins the thread.
    streamedLog.stop();
    return collected;
  }

  @Test
  void redirectsCompleteTimestampedMessages() throws InterruptedException {
    List<String> messages =
        redirect(
            "2999-01-01T00:00:00.000Z line A\n2999-01-01T00:00:01.000Z line B\n",
            new StreamedLogOptions());
    assertEquals(
        List.of("2999-01-01T00:00:00.000Z line A", "2999-01-01T00:00:01.000Z line B"), messages);
  }

  @Test
  void keepsMultiLineMessagesTogether() throws InterruptedException {
    // A message whose body spans multiple lines must not be split at the inner newline; only a
    // leading timestamp starts a new message.
    List<String> messages =
        redirect(
            "2999-01-01T00:00:00.000Z first line\ncontinued\n2999-01-01T00:00:01.000Z second\n",
            new StreamedLogOptions());
    assertEquals(2, messages.size());
    assertEquals("2999-01-01T00:00:00.000Z first line\ncontinued", messages.get(0));
    assertEquals("2999-01-01T00:00:01.000Z second", messages.get(1));
  }

  @Test
  void flushesFinalUnterminatedMessage() throws InterruptedException {
    List<String> messages =
        redirect("2999-01-01T00:00:00.000Z only line, no newline", new StreamedLogOptions());
    assertEquals(List.of("2999-01-01T00:00:00.000Z only line, no newline"), messages);
  }

  @Test
  void fromStartFalseDropsOldMessages() throws InterruptedException {
    // relevancyTimeLimit is set to "now" at construction, so the year-2000 line is dropped and the
    // year-2999 line is kept.
    List<String> messages =
        redirect(
            "2000-01-01T00:00:00.000Z old\n2999-01-01T00:00:00.000Z new\n",
            new StreamedLogOptions().fromStart(false));
    assertEquals(List.of("2999-01-01T00:00:00.000Z new"), messages);
  }

  @Test
  void defaultDestinationDoesNotThrow() throws InterruptedException {
    // With no toLog, getStreamedLog fetches the run + Actor to build the prefix, then logs to JUL.
    MockBackend backend =
        MockBackend.ofConstant(
            200, "{\"data\":{\"id\":\"run123\",\"actId\":\"act1\",\"name\":\"a\"}}");
    backend.scriptStream(200, "2999-01-01T00:00:00.000Z hello\n");
    StreamedLog streamedLog = client(backend).run("run123").getStreamedLog();
    streamedLog.start();
    streamedLog.stop();
    // No assertion on output (goes to java.util.logging); the point is it runs without throwing.
  }

  @Test
  void startTwiceThrows() throws InterruptedException {
    MockBackend backend = MockBackend.ofConstant(200, "");
    backend.scriptStream(200, "2999-01-01T00:00:00.000Z x\n");
    StreamedLog streamedLog =
        client(backend).run("run123").getStreamedLog(new StreamedLogOptions().toLog(m -> {}));
    streamedLog.start();
    assertThrows(IllegalStateException.class, streamedLog::start);
    streamedLog.stop();
  }

  @Test
  void stopWithoutStartThrows() {
    MockBackend backend = MockBackend.ofConstant(200, "");
    StreamedLog streamedLog =
        client(backend).run("run123").getStreamedLog(new StreamedLogOptions().toLog(m -> {}));
    assertThrows(IllegalStateException.class, streamedLog::stop);
  }

  @Test
  void closeWithoutStartIsNoOp() {
    MockBackend backend = MockBackend.ofConstant(200, "");
    StreamedLog streamedLog =
        client(backend).run("run123").getStreamedLog(new StreamedLogOptions().toLog(m -> {}));
    // close() on a never-started helper must not throw (supports try-with-resources).
    streamedLog.close();
  }

  @Test
  void deliversLastMessageWhenStoppingLiveStream() throws InterruptedException {
    // Regression test for a live stream that never reaches end-of-stream on its own. The reader
    // buffers three complete lines (a, b, c): a and b are emitted immediately while c is retained
    // as the possibly-still-growing last message. Then the read blocks. stop() closes the stream,
    // which unblocks the read with an IOException; the retained last message (c) must still be
    // flushed and delivered. A finite ByteArrayInputStream cannot exercise this because read()
    // returns -1 by itself, so we drive a purpose-built blocking stream instead.
    String body =
        "2999-01-01T00:00:00.000Z a\n"
            + "2999-01-01T00:00:01.000Z b\n"
            + "2999-01-01T00:00:02.000Z c\n";
    BlockingStream stream = new BlockingStream(body);
    MockBackend backend = MockBackend.ofConstant(200, "");
    backend.scriptStream(200, stream);
    List<String> collected = new CopyOnWriteArrayList<>();
    StreamedLog streamedLog =
        client(backend).run("run123").getStreamedLog(new StreamedLogOptions().toLog(collected::add));
    streamedLog.start();
    // Wait until a and b have been redirected, which proves the reader has consumed the body and is
    // now blocked with c held back as the pending last message.
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (collected.size() < 2 && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
    assertEquals(2, collected.size(), "a and b should be redirected before stop()");
    streamedLog.stop();
    assertEquals(
        List.of(
            "2999-01-01T00:00:00.000Z a",
            "2999-01-01T00:00:01.000Z b",
            "2999-01-01T00:00:02.000Z c"),
        collected,
        "the retained last message must be delivered after stop()");
  }

  @Test
  void closeStopsActiveRedirection() throws InterruptedException {
    MockBackend backend = MockBackend.ofConstant(200, "");
    backend.scriptStream(200, "2999-01-01T00:00:00.000Z x\n");
    List<String> collected = new CopyOnWriteArrayList<>();
    try (StreamedLog streamedLog =
        client(backend)
            .run("run123")
            .getStreamedLog(new StreamedLogOptions().toLog(collected::add))) {
      streamedLog.start();
      // give the finite stream a moment to be consumed
      Thread.sleep(Duration.ofMillis(50).toMillis());
    }
    assertTrue(collected.contains("2999-01-01T00:00:00.000Z x"));
  }

  /**
   * An {@link InputStream} that serves a fixed payload once and then blocks on {@code read()} until
   * it is closed, throwing an {@link IOException} when unblocked by the close. This models a live
   * log stream that produces some bytes and then stays open with no further data until the client
   * stops redirection - the case a finite {@link java.io.ByteArrayInputStream} cannot reproduce.
   */
  private static final class BlockingStream extends InputStream {
    private final byte[] data;
    private int pos;
    private final CountDownLatch closed = new CountDownLatch(1);

    BlockingStream(String data) {
      this.data = data.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {
      if (pos < data.length) {
        int n = Math.min(len, data.length - pos);
        System.arraycopy(data, pos, b, off, n);
        pos += n;
        return n;
      }
      // Payload exhausted: block like a live stream awaiting more bytes, until close() unblocks us.
      try {
        closed.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      throw new IOException("stream closed");
    }

    @Override
    public int read() throws IOException {
      byte[] one = new byte[1];
      int n = read(one, 0, 1);
      return n == -1 ? -1 : one[0] & 0xff;
    }

    @Override
    public void close() {
      closed.countDown();
    }
  }
}
