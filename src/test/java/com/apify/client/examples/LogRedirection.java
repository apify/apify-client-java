package com.apify.client.examples;

import com.apify.client.ApifyClient;
import com.apify.client.actor.ActorStartOptions;
import com.apify.client.log.StreamedLog;
import com.apify.client.run.ActorRun;
import com.apify.client.run.RunClient;

/**
 * Starts an Actor without waiting, then redirects its live log to a logger in real time (log
 * redirection) until the run finishes.
 */
public final class LogRedirection {
  private LogRedirection() {}

  public static void main(String[] args) {
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));

    // Start the Actor and return immediately (do not wait for it to finish).
    ActorRun run = client.actor("apify/hello-world").start(null, new ActorStartOptions());
    RunClient runClient = client.run(run.getId());

    // Redirect the run's live log to the default per-run logger while we wait for it to finish.
    try (StreamedLog streamedLog = runClient.getStreamedLog()) {
      streamedLog.start();
      runClient.waitForFinish(120L);
    }
  }
}
