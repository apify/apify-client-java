package com.apify.client;

/** An output format for {@link DatasetClient#downloadItems}. */
public enum DownloadItemsFormat {
  /** JSON array. */
  JSON("json"),
  /** Newline-delimited JSON. */
  JSONL("jsonl"),
  /** Comma-separated values. */
  CSV("csv"),
  /** Microsoft Excel (XLSX) workbook. */
  XLSX("xlsx"),
  /** XML. */
  XML("xml"),
  /** RSS feed. */
  RSS("rss"),
  /** HTML table. */
  HTML("html");

  private final String wireValue;

  DownloadItemsFormat(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The value sent in the {@code format} query parameter. */
  public String wireValue() {
    return wireValue;
  }
}
