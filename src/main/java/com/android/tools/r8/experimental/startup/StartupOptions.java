// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import static com.android.tools.r8.utils.SystemPropertyUtils.parseSystemPropertyForDevelopmentOrDefault;

public class StartupOptions {

  /**
   * When enabled, all startup classes will be placed in the primary classes.dex file. All other
   * (non-startup) classes will be placed in classes2.dex, ..., classesN.dex.
   */
  private boolean enableMinimalStartupDex =
      parseSystemPropertyForDevelopmentOrDefault(
          "com.android.tools.r8.startup.minimalstartupdex", false);

  /**
   * When enabled, optimizations crossing the startup/non-startup boundary will be allowed.
   *
   * <p>The disabling of this may help to avoid that more code may be loaded during startup as a
   * result of optimizations such as inlining and class merging.
   */
  private boolean enableStartupBoundaryOptimizations =
      parseSystemPropertyForDevelopmentOrDefault(
          "com.android.tools.r8.startup.boundaryoptimizations", false);

  /**
   * When enabled, each method that is not classified as a startup method at the end of compilation
   * will be changed to have a throwing method body.
   *
   * <p>This is useful for testing if a given startup list is complete (and that R8 correctly
   * rewrites the startup list in presence of optimizations).
   */
  private boolean enableStartupCompletenessCheckForTesting =
      parseSystemPropertyForDevelopmentOrDefault(
          "com.android.tools.r8.startup.completenesscheck", false);

  /**
   * When enabled, the layout of the primary dex file will be generated using the startup list,
   * using {@link com.android.tools.r8.dex.StartupMixedSectionLayoutStrategy}.
   */
  private boolean enableStartupLayoutOptimizations =
      parseSystemPropertyForDevelopmentOrDefault("com.android.tools.r8.startup.layout", true);

  private StartupConfiguration startupConfiguration;

  public boolean isMinimalStartupDexEnabled() {
    return enableMinimalStartupDex;
  }

  public StartupOptions setEnableMinimalStartupDex(boolean enableMinimalStartupDex) {
    this.enableMinimalStartupDex = enableMinimalStartupDex;
    return this;
  }

  public boolean isStartupBoundaryOptimizationsEnabled() {
    return enableStartupBoundaryOptimizations;
  }

  public StartupOptions setEnableStartupBoundaryOptimizations(
      boolean enableStartupBoundaryOptimizations) {
    this.enableStartupBoundaryOptimizations = enableStartupBoundaryOptimizations;
    return this;
  }

  public boolean isStartupLayoutOptimizationsEnabled() {
    return enableStartupLayoutOptimizations;
  }

  public boolean isStartupCompletenessCheckForTestingEnabled() {
    return enableStartupCompletenessCheckForTesting;
  }

  public StartupOptions setEnableStartupCompletenessCheckForTesting() {
    return setEnableStartupCompletenessCheckForTesting(true);
  }

  public StartupOptions setEnableStartupCompletenessCheckForTesting(
      boolean enableStartupCompletenessCheckForTesting) {
    this.enableStartupCompletenessCheckForTesting = enableStartupCompletenessCheckForTesting;
    return this;
  }

  public boolean hasStartupConfiguration() {
    return startupConfiguration != null;
  }

  public StartupConfiguration getStartupConfiguration() {
    return startupConfiguration;
  }

  public StartupOptions setStartupConfiguration(StartupConfiguration startupConfiguration) {
    this.startupConfiguration = startupConfiguration;
    return this;
  }
}
