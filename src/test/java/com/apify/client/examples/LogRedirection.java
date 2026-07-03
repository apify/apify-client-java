package com.apify.client.examples;

import com.apify.client.ActorRun;
import com.apify.client.ActorStartOptions;
import com.apify.client.ApifyClient;
import java.io.InputStream;

/**
 * Starts an Actor without waiting, then streams its log output to stdout in real time (log
 * redirection).
 */
public final class LogRedirection {
  private LogRedirection() {}

  public static void main(String[] args) throws Exception {
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));

    // Start the Actor and return immediately (do not wait for it to finish).
    ActorRun run = client.actor("apify/hello-world").start(null, new ActorStartOptions());

    // Open a live (raw) log stream and copy it to stdout as the run produces output.
    try (InputStream stream = client.run(run.getId()).getStreamedLog()) {
      stream.transferTo(System.out);
    }
  }
}
