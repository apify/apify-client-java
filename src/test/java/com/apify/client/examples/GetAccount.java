package com.apify.client.examples;

import com.apify.client.ApifyClient;
import com.apify.client.user.User;
import java.util.Optional;

/**
 * Fetches and prints the current user's account details.
 *
 * <p>Run with: {@code APIFY_TOKEN=<your-token> mvn ...} or by executing {@link #main(String[])}.
 */
public final class GetAccount {
  private GetAccount() {}

  public static void main(String[] args) {
    ApifyClient client = ApifyClient.create(System.getenv("APIFY_TOKEN"));
    Optional<User> user = client.me().get();
    if (user.isEmpty()) {
      throw new IllegalStateException("current user not found");
    }
    System.out.println("Account ID: " + user.get().getId());
    System.out.println("Username:   " + user.get().getUsername());
    // email/plan/proxy/... are only populated for me() (not user(id)); any remaining unmodelled
    // account field is still available via the untyped extras map that getExtra() returns.
    System.out.println("Email:      " + user.get().getEmail());
  }
}
