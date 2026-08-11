package com.apify.client.task;

import com.apify.client.ApifyResource;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Public-facing display configuration of a task's public landing page.
 *
 * <p>The task is published when {@link #getPublishedAt()} is set and unpublished when it is {@code
 * null}. {@code publishedAt} is read-only - use {@link TaskClient#publish()} and {@link
 * TaskClient#unpublish()} to change the publication state.
 *
 * <p>Not part of the documented {@code Task} schema in the OpenAPI spec, but the API returns it in
 * practice (mirroring the reference JS client's {@code TaskPublicConfig}).
 */
public final class TaskPublicConfig extends ApifyResource {
  private Instant publishedAt;
  private String seoTitle;
  private String seoDescription;
  private String categorization;
  private List<String> inputSchemaFields;
  private String datasetName;
  private String datasetView;

  /** When the task was published, or {@code null} if it is currently unpublished. */
  public Instant getPublishedAt() {
    return publishedAt;
  }

  /** The SEO title shown on the task's public landing page. */
  public String getSeoTitle() {
    return seoTitle;
  }

  /** The SEO description shown on the task's public landing page. */
  public String getSeoDescription() {
    return seoDescription;
  }

  /** The category the task is listed under on its public landing page. */
  public String getCategorization() {
    return categorization;
  }

  /** Which input schema fields are shown on the public landing page. */
  public List<String> getInputSchemaFields() {
    // Null-coalesce: Jackson binds directly to the (private) `inputSchemaFields` field for
    // deserialization, which can leave it null (field absent or explicit `null` in the response).
    // Unmodifiable wrapper: avoid exposing the backing list for external mutation.
    return inputSchemaFields == null ? List.of() : Collections.unmodifiableList(inputSchemaFields);
  }

  /** The name of the dataset shown on the public landing page, if any. */
  public String getDatasetName() {
    return datasetName;
  }

  /** Which view of the dataset is shown on the public landing page, if any. */
  public String getDatasetView() {
    return datasetView;
  }
}
