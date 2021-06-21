// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.features.FeatureSplitConfiguration;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryConfiguration;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class DumpOptions {

  // The following keys and values should not be changed to keep the dump utility backward
  // compatible with previous versions. They are also used by the python script compileDump and
  // the corresponding CompileDumpCompatR8 java class.
  private static final String TOOL_KEY = "tool";
  private static final String MODE_KEY = "mode";
  private static final String DEBUG_MODE_VALUE = "debug";
  private static final String RELEASE_MODE_VALUE = "release";
  private static final String MIN_API_KEY = "min-api";
  private static final String OPTIMIZE_MULTIDEX_FOR_LINEAR_ALLOC_KEY =
      "optimize-multidex-for-linear-alloc";
  private static final String THREAD_COUNT_KEY = "thread-count";
  private static final String DESUGAR_STATE_KEY = "desugar-state";
  private static final String INTERMEDIATE_KEY = "intermediate";
  private static final String INCLUDE_DATA_RESOURCES_KEY = "include-data-resources";
  private static final String TREE_SHAKING_KEY = "tree-shaking";
  private static final String MINIFICATION_KEY = "minification";
  private static final String FORCE_PROGUARD_COMPATIBILITY_KEY = "force-proguard-compatibility";

  private final Tool tool;
  private final CompilationMode compilationMode;
  private final int minApi;
  private final boolean optimizeMultidexForLinearAlloc;
  private final int threadCount;
  private final DesugarState desugarState;
  private final Optional<Boolean> intermediate;
  private final Optional<Boolean> includeDataResources;
  private final Optional<Boolean> treeShaking;
  private final Optional<Boolean> minification;
  private final Optional<Boolean> forceProguardCompatibility;

  // Dump if present.
  private final DesugaredLibraryConfiguration desugaredLibraryConfiguration;
  private final FeatureSplitConfiguration featureSplitConfiguration;
  private final ProguardConfiguration proguardConfiguration;
  private final List<ProguardConfigurationRule> mainDexKeepRules;

  // Reporting only.
  private final boolean dumpInputToFile;

  private DumpOptions(
      Tool tool,
      CompilationMode compilationMode,
      int minAPI,
      DesugaredLibraryConfiguration desugaredLibraryConfiguration,
      boolean optimizeMultidexForLinearAlloc,
      int threadCount,
      DesugarState desugarState,
      Optional<Boolean> intermediate,
      Optional<Boolean> includeDataResources,
      Optional<Boolean> treeShaking,
      Optional<Boolean> minification,
      Optional<Boolean> forceProguardCompatibility,
      FeatureSplitConfiguration featureSplitConfiguration,
      ProguardConfiguration proguardConfiguration,
      List<ProguardConfigurationRule> mainDexKeepRules,
      boolean dumpInputToFile) {
    this.tool = tool;
    this.compilationMode = compilationMode;
    this.minApi = minAPI;
    this.desugaredLibraryConfiguration = desugaredLibraryConfiguration;
    this.optimizeMultidexForLinearAlloc = optimizeMultidexForLinearAlloc;
    this.threadCount = threadCount;
    this.desugarState = desugarState;
    this.intermediate = intermediate;
    this.includeDataResources = includeDataResources;
    this.treeShaking = treeShaking;
    this.minification = minification;
    this.forceProguardCompatibility = forceProguardCompatibility;
    this.featureSplitConfiguration = featureSplitConfiguration;
    this.proguardConfiguration = proguardConfiguration;
    this.mainDexKeepRules = mainDexKeepRules;
    this.dumpInputToFile = dumpInputToFile;
  }

  public String dumpOptions() {
    StringBuilder builder = new StringBuilder();
    addDumpEntry(builder, TOOL_KEY, tool.name());
    // We keep the following values for backward compatibility.
    addDumpEntry(
        builder,
        MODE_KEY,
        compilationMode == CompilationMode.DEBUG ? DEBUG_MODE_VALUE : RELEASE_MODE_VALUE);
    addDumpEntry(builder, MIN_API_KEY, minApi);
    addDumpEntry(builder, OPTIMIZE_MULTIDEX_FOR_LINEAR_ALLOC_KEY, optimizeMultidexForLinearAlloc);
    if (threadCount != ThreadUtils.NOT_SPECIFIED) {
      addDumpEntry(builder, THREAD_COUNT_KEY, threadCount);
    }
    addDumpEntry(builder, DESUGAR_STATE_KEY, desugarState);
    addOptionalDumpEntry(builder, INTERMEDIATE_KEY, intermediate);
    addOptionalDumpEntry(builder, INCLUDE_DATA_RESOURCES_KEY, includeDataResources);
    addOptionalDumpEntry(builder, TREE_SHAKING_KEY, treeShaking);
    addOptionalDumpEntry(builder, MINIFICATION_KEY, minification);
    addOptionalDumpEntry(builder, FORCE_PROGUARD_COMPATIBILITY_KEY, forceProguardCompatibility);
    return builder.toString();
  }

  private void addOptionalDumpEntry(StringBuilder builder, String key, Optional<?> optionalValue) {
    optionalValue.ifPresent(bool -> addDumpEntry(builder, key, bool));
  }

  private void addDumpEntry(StringBuilder builder, String key, Object value) {
    builder.append(key).append("=").append(value).append("\n");
  }

  private boolean hasDesugaredLibraryConfiguration() {
    return desugaredLibraryConfiguration != null
        && desugaredLibraryConfiguration
            != DesugaredLibraryConfiguration.EMPTY_DESUGARED_LIBRARY_CONFIGURATION;
  }

  public String getDesugaredLibraryJsonSource() {
    if (hasDesugaredLibraryConfiguration()) {
      return desugaredLibraryConfiguration.getJsonSource();
    }
    return null;
  }

  public FeatureSplitConfiguration getFeatureSplitConfiguration() {
    return featureSplitConfiguration;
  }

  public String getParsedProguardConfiguration() {
    return proguardConfiguration == null ? null : proguardConfiguration.getParsedConfiguration();
  }

  public boolean hasMainDexKeepRules() {
    return mainDexKeepRules != null && !mainDexKeepRules.isEmpty();
  }

  public List<ProguardConfigurationRule> getMainDexKeepRules() {
    return mainDexKeepRules;
  }

  public boolean dumpInputToFile() {
    return dumpInputToFile;
  }

  public static Builder builder(Tool tool) {
    return new Builder(tool);
  }

  public static class Builder {
    private final Tool tool;
    private CompilationMode compilationMode;
    private int minApi;
    private boolean optimizeMultidexForLinearAlloc;
    private int threadCount;
    private DesugarState desugarState;
    private Optional<Boolean> intermediate = Optional.empty();
    private Optional<Boolean> includeDataResources = Optional.empty();
    private Optional<Boolean> treeShaking = Optional.empty();
    private Optional<Boolean> minification = Optional.empty();
    private Optional<Boolean> forceProguardCompatibility = Optional.empty();
    // Dump if present.
    private DesugaredLibraryConfiguration desugaredLibraryConfiguration;
    private FeatureSplitConfiguration featureSplitConfiguration;
    private ProguardConfiguration proguardConfiguration;
    private List<ProguardConfigurationRule> mainDexKeepRules;

    // Reporting only.
    private boolean dumpInputToFile;

    public Builder(Tool tool) {
      this.tool = tool;
    }

    public Builder setCompilationMode(CompilationMode compilationMode) {
      this.compilationMode = compilationMode;
      return this;
    }

    public Builder setMinApi(int minAPI) {
      this.minApi = minAPI;
      return this;
    }

    public Builder setDesugaredLibraryConfiguration(
        DesugaredLibraryConfiguration desugaredLibraryConfiguration) {
      this.desugaredLibraryConfiguration = desugaredLibraryConfiguration;
      return this;
    }

    public Builder setOptimizeMultidexForLinearAlloc(boolean optimizeMultidexForLinearAlloc) {
      this.optimizeMultidexForLinearAlloc = optimizeMultidexForLinearAlloc;
      return this;
    }

    public Builder setThreadCount(int threadCount) {
      this.threadCount = threadCount;
      return this;
    }

    public Builder setDesugarState(DesugarState desugarState) {
      this.desugarState = desugarState;
      return this;
    }

    public Builder setIntermediate(boolean intermediate) {
      this.intermediate = Optional.of(intermediate);
      return this;
    }

    public Builder setIncludeDataResources(Optional<Boolean> includeDataResources) {
      this.includeDataResources = includeDataResources;
      return this;
    }

    public Builder setForceProguardCompatibility(boolean forceProguardCompatibility) {
      this.forceProguardCompatibility = Optional.of(forceProguardCompatibility);
      return this;
    }

    public Builder setMinification(boolean minification) {
      this.minification = Optional.of(minification);
      return this;
    }

    public Builder setTreeShaking(boolean treeShaking) {
      this.treeShaking = Optional.of(treeShaking);
      return this;
    }

    public Builder setDumpInputToFile(boolean dumpInputToFile) {
      this.dumpInputToFile = dumpInputToFile;
      return this;
    }

    public Builder setFeatureSplitConfiguration(
        FeatureSplitConfiguration featureSplitConfiguration) {
      this.featureSplitConfiguration = featureSplitConfiguration;
      return this;
    }

    public Builder setProguardConfiguration(ProguardConfiguration proguardConfiguration) {
      this.proguardConfiguration = proguardConfiguration;
      return this;
    }

    public Builder setMainDexKeepRules(List<ProguardConfigurationRule> mainDexKeepRules) {
      this.mainDexKeepRules = mainDexKeepRules;
      return this;
    }

    public DumpOptions build() {
      return new DumpOptions(
          tool,
          compilationMode,
          minApi,
          desugaredLibraryConfiguration,
          optimizeMultidexForLinearAlloc,
          threadCount,
          desugarState,
          intermediate,
          includeDataResources,
          treeShaking,
          minification,
          forceProguardCompatibility,
          featureSplitConfiguration,
          proguardConfiguration,
          mainDexKeepRules,
          dumpInputToFile);
    }
  }
}
