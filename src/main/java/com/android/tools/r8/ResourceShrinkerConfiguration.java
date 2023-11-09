// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/**
 * Resource shrinker configuration. Allows building an immutable structure of resource shrinker
 * settings.
 *
 * <p>A {@link ResourceShrinkerConfiguration} can be added to a {@link R8Command} change the way
 * resource shrinking is performed.
 *
 * <p>To build a {@link ResourceShrinkerConfiguration} use the {@link
 * ResourceShrinkerConfiguration.Builder} class, available through the {@link R8Command.Builder}.
 * For example:
 *
 * <pre>
 *   R8Command command = R8Command.builder()
 *     .addProgramFiles(path1, path2)
 *     .setMode(CompilationMode.RELEASE)
 *     .setProgramConsumer(programConsumer)
 *     .setResourceShrinkerConfiguration(builder -> builder
 *         .enableOptimizedShrinkingWithR8()
 *         .build())
 *     .build();
 * </pre>
 */
@KeepForApi
public class ResourceShrinkerConfiguration {
  static ResourceShrinkerConfiguration DEFAULT_CONFIGURATION =
      new ResourceShrinkerConfiguration(false, true);

  private final boolean optimizedShrinking;
  private final boolean preciseShrinking;

  private ResourceShrinkerConfiguration(boolean optimizedShrinking, boolean preciseShrinking) {
    this.optimizedShrinking = optimizedShrinking;
    this.preciseShrinking = preciseShrinking;
  }

  static Builder builder(DiagnosticsHandler handler) {
    return new Builder();
  }

  public boolean isOptimizedShrinking() {
    return optimizedShrinking;
  }

  public boolean isPreciseShrinking() {
    return preciseShrinking;
  }

  /**
   * Builder for constructing a ResourceShrinkerConfiguration.
   *
   * <p>A builder is obtained by calling setResourceShrinkerConfiguration on a {@link
   * R8Command.Builder}.
   */
  @KeepForApi
  public static class Builder {

    private boolean optimizedShrinking = false;
    private boolean preciseShrinking = true;

    private Builder() {}

    /**
     * Enable R8 based resource shrinking.
     *
     * <p>If this is not set, r8 will use resource shrinking legacy mode where resource shrinking is
     * done after code has been generated. This is consistent with a setup where resource shrinking
     * is run seperately from R8.
     *
     * <p>Setting this option allows R8 to shrink resources as part of its normal compilation,
     * tracing resources throughout the pipeline.
     */
    public Builder enableOptimizedShrinkingWithR8() {
      assert preciseShrinking;
      this.optimizedShrinking = true;
      return this;
    }

    /**
     * Disable precise shrinking.
     *
     * <p>The resource table will not be rewritten. Unused entries in the res folder will be
     * replaced by small dummy files.
     */
    @Deprecated
    public Builder disablePreciseShrinking() {
      assert !optimizedShrinking;
      this.preciseShrinking = false;
      return this;
    }

    /** Build and return the {@link ResourceShrinkerConfiguration} */
    public ResourceShrinkerConfiguration build() {
      return new ResourceShrinkerConfiguration(optimizedShrinking, preciseShrinking);
    }
  }
}
