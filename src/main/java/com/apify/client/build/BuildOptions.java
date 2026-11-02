package com.apify.client.build;

import com.apify.client.ApifyResource;

/** The configuration options actually applied to a {@link Build}. */
public final class BuildOptions extends ApifyResource {
  private Boolean useCache;
  private Boolean betaPackages;
  private Long memoryMbytes;
  private Long diskMbytes;
  private String tag;
  private Boolean debug;

  /** Whether the build reused a cached image layer. */
  public Boolean getUseCache() {
    return useCache;
  }

  /** Whether the build used beta versions of Actor SDK packages. */
  public Boolean getBetaPackages() {
    return betaPackages;
  }

  /** The memory allocation applied to the build, in megabytes. */
  public Long getMemoryMbytes() {
    return memoryMbytes;
  }

  /** The disk allocation applied to the build, in megabytes. */
  public Long getDiskMbytes() {
    return diskMbytes;
  }

  /** The build tag requested for the build. */
  public String getTag() {
    return tag;
  }

  /** Whether the build was run in debug mode. */
  public Boolean getDebug() {
    return debug;
  }
}
