// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable structure for specifying input and output of a feature split.
 *
 * <p>A {@link FeatureSplit} can be added to a {@link R8Command} to enable the building of dynamic
 * apps.
 *
 * <p>To build a {@link FeatureSplit} use the {@link FeatureSplit.Builder} class, available through
 * the {@link R8Command.Builder}. For example:
 *
 * <pre>
 *   R8Command command = R8Command.builder()
 *     .addProgramFiles(path1, path2)
 *     .setMode(CompilationMode.RELEASE)
 *     .setProgramConsumer(baseConsumer)
 *     .addFeatureSplit(builder -> builder
 *         .addProgramResourceProvider(programResourceProvider)
 *         .setProgramConsumer(featureConsumer)
 *         .build())
 *     .build();
 * </pre>
 */
@KeepForApi
public class FeatureSplit {

  public static final FeatureSplit BASE =
      new FeatureSplit(null, null, null, null) {
        @Override
        public boolean isBase() {
          return true;
        }
      };

  public static final FeatureSplit BASE_STARTUP =
      new FeatureSplit(null, null, null, null) {
        @Override
        public boolean isBase() {
          return true;
        }

        @Override
        public boolean isStartupBase() {
          return true;
        }
      };

  private ProgramConsumer programConsumer;
  private final List<ProgramResourceProvider> programResourceProviders;
  private final AndroidResourceProvider androidResourceProvider;
  private final AndroidResourceConsumer androidResourceConsumer;

  private FeatureSplit(
      ProgramConsumer programConsumer,
      List<ProgramResourceProvider> programResourceProviders,
      AndroidResourceProvider androidResourceProvider,
      AndroidResourceConsumer androidResourceConsumer) {
    this.programConsumer = programConsumer;
    this.programResourceProviders = programResourceProviders;
    this.androidResourceProvider = androidResourceProvider;
    this.androidResourceConsumer = androidResourceConsumer;
  }

  public boolean isBase() {
    return false;
  }

  public boolean isStartupBase() {
    return false;
  }

  void internalSetProgramConsumer(ProgramConsumer consumer) {
    this.programConsumer = consumer;
  }

  public List<ProgramResourceProvider> getProgramResourceProviders() {
    return programResourceProviders;
  }

  public ProgramConsumer getProgramConsumer() {
    return programConsumer;
  }

  static Builder builder(DiagnosticsHandler handler) {
    return new Builder(handler);
  }

  public AndroidResourceProvider getAndroidResourceProvider() {
    return androidResourceProvider;
  }

  public AndroidResourceConsumer getAndroidResourceConsumer() {
    return androidResourceConsumer;
  }

  /**
   * Builder for constructing a FeatureSplit.
   *
   * <p>A builder is obtained by calling addFeatureSplit on a {@link R8Command.Builder}.
   */
  @KeepForApi
  public static class Builder {
    private ProgramConsumer programConsumer;
    private final List<ProgramResourceProvider> programResourceProviders = new ArrayList<>();
    private AndroidResourceProvider androidResourceProvider;
    private AndroidResourceConsumer androidResourceConsumer;

    @SuppressWarnings("UnusedVariable")
    private final DiagnosticsHandler handler;


    private Builder(DiagnosticsHandler handler) {
      this.handler = handler;
    }

    /**
     * Set the program consumer.
     *
     * <p>Setting the program consumer will override any previous set consumer. This consumer is
     * specific to the feature, i.e., it will only get output from the
     *
     * @param programConsumer Program consumer to set as current.
     */
    public Builder setProgramConsumer(ProgramConsumer programConsumer) {
      this.programConsumer = programConsumer;
      return this;
    }

    /**
     * Add a resource provider for program resources.
     *
     * @param programResourceProvider A provider of program resources.
     */
    public Builder addProgramResourceProvider(ProgramResourceProvider programResourceProvider) {
      this.programResourceProviders.add(programResourceProvider);
      return this;
    }

    public Builder setAndroidResourceProvider(AndroidResourceProvider androidResourceProvider) {
      this.androidResourceProvider = androidResourceProvider;
      return this;
    }

    public Builder setAndroidResourceConsumer(AndroidResourceConsumer androidResourceConsumer) {
      this.androidResourceConsumer = androidResourceConsumer;
      return this;
    }

    /** Build and return the {@link FeatureSplit} */
    public FeatureSplit build() {
      return new FeatureSplit(
          programConsumer,
          programResourceProviders,
          androidResourceProvider,
          androidResourceConsumer);
    }
  }
}
