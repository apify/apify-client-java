package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
}
