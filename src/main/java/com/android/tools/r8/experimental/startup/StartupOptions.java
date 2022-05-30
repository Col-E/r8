// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import static com.android.tools.r8.utils.SystemPropertyUtils.getSystemPropertyForDevelopment;
import static com.android.tools.r8.utils.SystemPropertyUtils.parseSystemPropertyForDevelopmentOrDefault;

public class StartupOptions {

  private boolean enableMinimalStartupDex =
      parseSystemPropertyForDevelopmentOrDefault(
          "com.android.tools.r8.startup.minimalstartupdex", false);
  private boolean enableStartupCompletenessCheckForTesting =
      parseSystemPropertyForDevelopmentOrDefault(
          "com.android.tools.r8.startup.completenesscheck", false);
  private boolean enableStartupInstrumentation =
      parseSystemPropertyForDevelopmentOrDefault("com.android.tools.r8.startup.instrument", false);
  private String startupInstrumentationTag =
      getSystemPropertyForDevelopment("com.android.tools.r8.startup.instrumentationtag");

  private StartupConfiguration startupConfiguration;

  public boolean hasStartupInstrumentationTag() {
    return startupInstrumentationTag != null;
  }

  public String getStartupInstrumentationTag() {
    return startupInstrumentationTag;
  }

  public boolean isMinimalStartupDexEnabled() {
    return enableMinimalStartupDex;
  }

  public StartupOptions setEnableMinimalStartupDex() {
    enableMinimalStartupDex = true;
    return this;
  }

  public boolean isStartupInstrumentationEnabled() {
    return enableStartupInstrumentation;
  }

  public StartupOptions setEnableStartupInstrumentation() {
    enableStartupInstrumentation = true;
    return this;
  }

  public boolean isStartupCompletenessCheckForTesting() {
    return enableStartupCompletenessCheckForTesting;
  }

  public StartupOptions setEnableStartupCompletenessCheckForTesting() {
    enableStartupCompletenessCheckForTesting = true;
    return this;
  }

  public boolean hasStartupConfiguration() {
    return startupConfiguration != null;
  }

  public StartupConfiguration getStartupConfiguration() {
    return startupConfiguration;
  }

  public void setStartupConfiguration(StartupConfiguration startupConfiguration) {
    this.startupConfiguration = startupConfiguration;
  }
}
