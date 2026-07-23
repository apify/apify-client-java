package com.apify.client;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.apify.client.examples.CreateBuildRunActor;
import com.apify.client.examples.GetAccount;
import com.apify.client.examples.IterateStore;
import com.apify.client.examples.LogRedirection;
import com.apify.client.examples.RunAndLastRunStorages;
import com.apify.client.examples.RunStoreActor;
import com.apify.client.examples.Storages;
import org.junit.jupiter.api.Test;

/**
 * Runs each documentation example end-to-end against the live API, so the docs stay runnable. Each
 * test is skipped when {@code APIFY_TOKEN} is unset.
 */
class ExamplesTest {

  private static void requireToken() {
    String token = System.getenv("APIFY_TOKEN");
    assumeTrue(token != null && !token.isEmpty(), "skipping: APIFY_TOKEN is not set");
  }

  @Test
  void getAccount() {
    requireToken();
    GetAccount.main(new String[] {});
  }

  @Test
  void storages() {
    requireToken();
    Storages.main(new String[] {});
  }

  @Test
  void runStoreActor() {
    requireToken();
    RunStoreActor.main(new String[] {});
  }

  @Test
  void runAndLastRunStorages() {
    requireToken();
    RunAndLastRunStorages.main(new String[] {});
  }

  @Test
  void iterateStore() throws Exception {
    requireToken();
    IterateStore.main(new String[] {});
  }

  @Test
  void logRedirection() throws Exception {
    requireToken();
    LogRedirection.main(new String[] {});
  }

  @Test
  void createBuildRunActor() {
    requireToken();
    CreateBuildRunActor.main(new String[] {});
  }
}
