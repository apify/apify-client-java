package com.apify.client;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** A client for a specific key-value store (and run-nested variants). */
public final class KeyValueStoreClient {
  private ResourceContext ctx;

  KeyValueStoreClient(HttpClientCore http, String baseUrl, String resourcePath, String id) {
    this.ctx = ResourceContext.single(http, baseUrl, resourcePath, id);
  }

  private KeyValueStoreClient(ResourceContext ctx) {
    this.ctx = ctx;
  }

  /** Creates a client for a run's default key-value store (nested path only, no ID). */
  static KeyValueStoreClient nested(HttpClientCore http, String base, String subPath) {
    return nested(http, base, subPath, null);
  }

  /** As {@link #nested(HttpClientCore, String, String)} but inheriting parent query params. */
  static KeyValueStoreClient nested(
      HttpClientCore http, String base, String subPath, QueryParams inherited) {
    return new KeyValueStoreClient(
        ResourceContext.collection(http, base, subPath).seedParams(inherited));
  }

  KeyValueStoreClient withPublicBase(String publicBaseUrl) {
    this.ctx = ctx.withPublicOrigin(publicBaseUrl);
    return this;
  }

  /** Fetches the store metadata, or empty if it does not exist. */
  public Optional<KeyValueStore> get() {
    return ctx.getResource("", new QueryParams(), KeyValueStore.class);
  }

  /** Updates the store metadata (e.g. name) and returns the updated object. */
  public KeyValueStore update(Object newFields) {
    return ctx.updateResource("", newFields, KeyValueStore.class);
  }

  /** Deletes the store. */
  public void delete() {
    ctx.deleteResource("");
  }

  /** Lists the keys stored in this key-value store. */
  public KeyValueStoreKeysPage listKeys(ListKeysOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    return ctx.getResourceRequired("keys", params, KeyValueStoreKeysPage.class);
  }

  /**
   * Returns a lazy iterator over this store's keys, fetching pages on demand via the cursor-based
   * ({@code exclusiveStartKey}) listing endpoint. The options' {@code limit} caps the total number
   * of keys yielded ({@code null} = all); any {@code exclusiveStartKey} sets the starting point.
   */
  public Iterator<KeyValueStoreKey> iterateKeys(ListKeysOptions options) {
    return new KeysIterator(options != null ? options : new ListKeysOptions());
  }

  /**
   * Lazily iterates over a store's keys via the cursor-based ({@code exclusiveStartKey}) listing.
   */
  private final class KeysIterator implements Iterator<KeyValueStoreKey> {
    private final ListKeysOptions options;
    private List<KeyValueStoreKey> buffer = List.of();
    private int pos;
    private String cursor;
    private Long remaining;
    private boolean exhausted;

    KeysIterator(ListKeysOptions options) {
      this.options = options;
      this.cursor = options.exclusiveStartKeyValue();
      Long limit = options.limitValue();
      this.remaining = limit != null && limit > 0 ? limit : null;
    }

    @Override
    public boolean hasNext() {
      while (pos >= buffer.size()) {
        if (exhausted) {
          return false;
        }
        fetchPage();
      }
      return true;
    }

    @Override
    public KeyValueStoreKey next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return buffer.get(pos++);
    }

    private void fetchPage() {
      QueryParams params = new QueryParams();
      // The API caps the page size; requesting `remaining` keeps the last page from overshooting
      // the
      // caller's total cap. A null limit lets the server choose its default page size.
      params.addLong("limit", remaining);
      params.addString("exclusiveStartKey", cursor);
      options.applyFilters(params);
      KeyValueStoreKeysPage page =
          ctx.getResourceRequired("keys", params, KeyValueStoreKeysPage.class);
      buffer = page.getItems();
      pos = 0;
      cursor = page.getNextExclusiveStartKey();
      if (remaining != null) {
        // Defensively trim the last page to the cap in case the server returned more than
        // requested.
        if (buffer.size() > remaining) {
          buffer = buffer.subList(0, remaining.intValue());
        }
        remaining -= buffer.size();
      }
      // Stop when the page is empty, there is no next cursor, or the total cap is reached.
      if (buffer.isEmpty()
          || cursor == null
          || cursor.isEmpty()
          || (remaining != null && remaining <= 0)) {
        exhausted = true;
      }
    }
  }

  /** Reports whether a record with the given key exists. */
  public boolean recordExists(String key) {
    return ctx.headExists("records/" + ResourceContext.encodePathSegment(key), new QueryParams());
  }

  /**
   * Fetches a record by key, or empty if it does not exist. Like the reference client, it requests
   * the record as an attachment so the API returns the raw bytes directly rather than redirecting.
   */
  public Optional<KeyValueStoreRecord> getRecord(String key) {
    return getRecord(key, new GetRecordOptions().attachment(true));
  }

  /** Fetches a record with explicit options (attachment, signature). */
  public Optional<KeyValueStoreRecord> getRecord(String key, GetRecordOptions options) {
    QueryParams params = new QueryParams();
    options.apply(params);
    ApiResponse resp = ctx.getRaw("records/" + ResourceContext.encodePathSegment(key), params);
    if (resp == null) {
      return Optional.empty();
    }
    String contentType = resp.headers.firstValue("Content-Type").orElse(null);
    return Optional.of(new KeyValueStoreRecord(key, resp.body, contentType));
  }

  /** Stores a record with raw bytes and the given content type. */
  public void setRecord(String key, byte[] value, String contentType) {
    setRecord(key, value, contentType, new SetRecordOptions());
  }

  /**
   * Stores a record with raw bytes and the given content type, honoring the given write options
   * ({@code timeoutSecs}, {@code doNotRetryTimeouts}).
   */
  public void setRecord(String key, byte[] value, String contentType, SetRecordOptions options) {
    java.time.Duration timeout =
        options.timeoutSecsValue() != null
            ? java.time.Duration.ofSeconds(options.timeoutSecsValue())
            : ctx.http.baseRequestTimeout();
    ctx.putRaw(
        "records/" + ResourceContext.encodePathSegment(key),
        new QueryParams(),
        value,
        contentType,
        timeout,
        options.doNotRetryTimeoutsValue());
  }

  /** Stores a record holding the JSON serialization of {@code value}. */
  public void setRecordJson(String key, Object value) {
    setRecord(key, Json.toBytes(value), ResourceContext.CONTENT_TYPE_JSON_CHARSET);
  }

  /** Deletes a record by key. */
  public void deleteRecord(String key) {
    ctx.deleteResource("records/" + ResourceContext.encodePathSegment(key));
  }

  /**
   * Builds a public URL for fetching the given record. It fetches the store, and if the store
   * exposes a URL-signing secret key (i.e. it is private), appends an HMAC-SHA256 signature so the
   * URL grants access without an API token. The URL is built from the configured public base URL.
   */
  public String getRecordPublicUrl(String key) {
    QueryParams params = new QueryParams();
    Optional<KeyValueStore> store = get();
    if (store.isPresent()) {
      String secret = DatasetClient.extractString(store.get().getExtra(), "urlSigningSecretKey");
      if (secret != null) {
        params.addString("signature", Signatures.createHmacSignature(secret, key));
      }
    }
    // Public URL = resource path + signature only, matching the JS reference.
    // Seeded filters are not carried; see DatasetClient.createItemsPublicUrl and
    // docs/storages.md for the last-run caveat.
    return params.applyToUrl(ctx.publicUrl("records/" + ResourceContext.encodePathSegment(key)));
  }

  /**
   * Builds a public URL for listing this store's keys. As with {@link #getRecordPublicUrl}, a
   * signature is appended for private stores. {@code expiresInSecs} optionally bounds the validity
   * of a signed URL ({@code null} for non-expiring).
   */
  public String createKeysPublicUrl(Long expiresInSecs) {
    return createKeysPublicUrl(new ListKeysOptions(), expiresInSecs);
  }

  /**
   * Builds a public URL for listing this store's keys, forwarding the given key-listing filters
   * ({@code limit}, {@code prefix}, {@code collection}, {@code exclusiveStartKey}) into the URL. As
   * with {@link #getRecordPublicUrl}, a signature is appended for private stores unless the caller
   * already supplied one. {@code expiresInSecs} optionally bounds the validity of a signed URL
   * ({@code null} for non-expiring).
   */
  public String createKeysPublicUrl(ListKeysOptions options, Long expiresInSecs) {
    QueryParams params = new QueryParams();
    options.apply(params);
    if (options.signatureValue() == null) {
      Optional<KeyValueStore> store = get();
      if (store.isPresent()) {
        String secret = DatasetClient.extractString(store.get().getExtra(), "urlSigningSecretKey");
        if (secret != null) {
          params.addString(
              "signature",
              Signatures.signStorageContent(secret, store.get().getId(), expiresInSecs));
        }
      }
    }
    // Public URL = resource path + the explicit key-listing options + signature, matching the JS
    // reference (see the last-run caveat documented in docs/storages.md).
    return params.applyToUrl(ctx.publicUrl("keys"));
  }
}
