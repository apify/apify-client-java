package com.apify.client;

/** Configures log retrieval/streaming. */
public final class LogOptions {
  private Boolean raw;
  private Boolean download;

  /** If {@code true}, return the unprocessed log content (no platform post-processing). */
  public LogOptions raw(Boolean raw) {
    this.raw = raw;
    return this;
  }

  /** If {@code true}, set Content-Disposition so the log is served as a download. */
  public LogOptions download(Boolean download) {
    this.download = download;
    return this;
  }

  void apply(QueryParams q) {
    q.addBool("raw", raw).addBool("download", download);
  }
}
