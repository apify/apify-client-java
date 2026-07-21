package com.apify.client.build;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The configuration options actually applied to a {@link Build}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BuildOptions {
  private Boolean useCache;
  private Boolean betaPackages;
  private Long memoryMbytes;
  private Long diskMbytes;

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
}
