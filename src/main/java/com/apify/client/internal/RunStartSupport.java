package com.apify.client.internal;

import com.apify.client.ApifyClient;
import com.apify.client.log.StreamedLog;
import com.apify.client.run.ActorRun;
import com.apify.client.run.RunClient;

/**
 * Shared implementation of "start a run" and "call: start, then wait, optionally streaming the log"
 * for every client that can start a run from a runnable resource (Actor, Task). Both resources'
 * start-options and call-options types opt in via {@link StartOptionsLike}/{@link CallOptionsLike},
 * so this single implementation backs {@code ActorClient}/{@code TaskClient} instead of each
 * duplicating the same ~90 lines.
 */
public final class RunStartSupport {

  private RunStartSupport() {}

  /**
   * Starts a run on {@code ctx}'s {@code runs} sub-resource and returns immediately with the
   * created run. {@code input} is any JSON-serializable value, or {@code null} for no input.
   */
  public static ActorRun start(ResourceContext ctx, Object input, StartOptionsLike options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    byte[] body = input == null ? null : Json.toBytes(input);
    return ctx.postWithBody("runs", params, body, options.contentTypeOrDefault(), ActorRun.class);
  }

  /**
   * Starts a run and waits (client-side polling) for it to finish, without log streaming. {@code
   * waitSecs} bounds the wait; {@code null} waits indefinitely.
   */
  public static ActorRun call(
      ApifyClient root,
      ResourceContext ctx,
      Object input,
      StartOptionsLike options,
      Long waitSecs) {
    ActorRun run = start(ctx, input, options);
    return root.run(run.getId()).waitForFinish(waitSecs);
  }

  /**
   * Starts a run and waits for it to finish, additionally streaming the run's log for the duration
   * of the wait by default, matching the reference client's {@code call} defaulting {@code
   * options.log} to {@code 'default'}. Log streaming is best-effort: if starting it fails (e.g. the
   * log is not yet available), the run still starts and is still waited for, just without
   * redirected log output.
   */
  public static <S extends StartOptionsLike> ActorRun callWithLogStreaming(
      ApifyClient root,
      ResourceContext ctx,
      Object input,
      CallOptionsLike<S> options,
      Long waitSecs) {
    ActorRun run = start(ctx, input, options.toStartOptions());
    RunClient runClient = root.run(run.getId());

    StreamedLog streamedLog = null;
    if (options.logStreamingEnabledValue()) {
      streamedLog = startStreamedLogQuietly(runClient, options);
    }
    try {
      return runClient.waitForFinish(waitSecs);
    } finally {
      if (streamedLog != null) {
        streamedLog.close();
      }
    }
  }

  /**
   * Starts {@code call}'s default log streaming, swallowing (rather than propagating) any failure
   * to open the live log stream, so a transient log-endpoint issue cannot abort the run itself.
   */
  private static <S extends StartOptionsLike> StreamedLog startStreamedLogQuietly(
      RunClient runClient, CallOptionsLike<S> options) {
    try {
      StreamedLog streamedLog =
          options.logOptionsValue() != null
              ? runClient.getStreamedLog(options.logOptionsValue())
              : runClient.getStreamedLog();
      streamedLog.start();
      return streamedLog;
    } catch (RuntimeException e) {
      return null;
    }
  }
}
