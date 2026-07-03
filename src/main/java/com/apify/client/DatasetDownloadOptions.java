package com.apify.client;

/**
 * Adds format-specific options for {@link DatasetClient#downloadItems} on top of the shared item
 * filtering/projection options.
 */
public final class DatasetDownloadOptions {
  private DatasetListItemsOptions items = new DatasetListItemsOptions();
  private Boolean attachment;
  private Boolean bom;
  private String delimiter;
  private Boolean skipHeaderRow;
  private String xmlRoot;
  private String xmlRow;
  private String feedTitle;
  private String feedDescription;

  /** The shared filtering/projection options. */
  public DatasetDownloadOptions items(DatasetListItemsOptions items) {
    this.items = items;
    return this;
  }

  /** Set {@code Content-Disposition: attachment} on the response. */
  public DatasetDownloadOptions attachment(Boolean attachment) {
    this.attachment = attachment;
    return this;
  }

  /** Prepend a UTF-8 BOM (useful for Excel-compatible CSV). */
  public DatasetDownloadOptions bom(Boolean bom) {
    this.bom = bom;
    return this;
  }

  /** The CSV field delimiter (default {@code ","}). */
  public DatasetDownloadOptions delimiter(String delimiter) {
    this.delimiter = delimiter;
    return this;
  }

  /** Omit the CSV header row. */
  public DatasetDownloadOptions skipHeaderRow(Boolean skipHeaderRow) {
    this.skipHeaderRow = skipHeaderRow;
    return this;
  }

  /** The name of the root XML element (default {@code "items"}). */
  public DatasetDownloadOptions xmlRoot(String xmlRoot) {
    this.xmlRoot = xmlRoot;
    return this;
  }

  /** The name of the per-item XML element (default {@code "item"}). */
  public DatasetDownloadOptions xmlRow(String xmlRow) {
    this.xmlRow = xmlRow;
    return this;
  }

  /** The title used for RSS/Atom feed exports. */
  public DatasetDownloadOptions feedTitle(String feedTitle) {
    this.feedTitle = feedTitle;
    return this;
  }

  /** The description used for RSS/Atom feed exports. */
  public DatasetDownloadOptions feedDescription(String feedDescription) {
    this.feedDescription = feedDescription;
    return this;
  }

  void apply(QueryParams q) {
    if (items != null) {
      items.apply(q);
    }
    q.addBool("attachment", attachment)
        .addBool("bom", bom)
        .addString("delimiter", delimiter)
        .addBool("skipHeaderRow", skipHeaderRow)
        .addString("xmlRoot", xmlRoot)
        .addString("xmlRow", xmlRow)
        .addString("feedTitle", feedTitle)
        .addString("feedDescription", feedDescription);
  }
}
