package com.apify.client;

/** Configures {@link KeyValueStoreClient#getRecord(String, GetRecordOptions)}. */
public final class GetRecordOptions {
  private Boolean attachment;
  private String signature;

  /** Controls the {@code Content-Disposition: attachment} behaviour. */
  public GetRecordOptions attachment(Boolean attachment) {
    this.attachment = attachment;
    return this;
  }

  /** A pre-shared URL signature granting access without an API token. */
  public GetRecordOptions signature(String signature) {
    this.signature = signature;
    return this;
  }

  void apply(QueryParams q) {
    q.addBool("attachment", attachment).addString("signature", signature);
  }
}
