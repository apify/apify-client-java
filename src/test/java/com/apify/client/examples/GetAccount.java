package com.apify.client.examples;

import com.apify.client.ApifyClient;
import com.apify.client.User;
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
    // Fields not modelled on User (email, plan, ...) live in the untyped extras map that
    // getExtra() returns as a Map<String, Object>.
    System.out.println("Email:      " + user.get().getExtra().get("email"));
  }
}
