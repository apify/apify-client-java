package com.apify.client;

/** An Actor as listed in the Apify Store. */
public final class ActorStoreListItem extends ApifyResource {
  private String id;
  private String name;
  private String username;
  private String title;

  /** The unique Actor ID. */
  public String getId() {
    return id;
  }

  /** The technical name of the Actor. */
  public String getName() {
    return name;
  }

  /** The username of the Actor's owner. */
  public String getUsername() {
    return username;
  }

  /** The human-readable title. */
  public String getTitle() {
    return title;
  }
}
