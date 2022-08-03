// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import static com.android.tools.r8.utils.SystemPropertyUtils.getSystemPropertyForDevelopment;
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
   * When enabled, each method will be instrumented to notify the startup InstrumentationServer that
   * it has been executed.
   *
   * <p>This will also inject the startup runtime library (i.e., the InstrumentationServer) into the
   * app.
   */
  private boolean enableStartupInstrumentation =
      parseSystemPropertyForDevelopmentOrDefault("com.android.tools.r8.startup.instrument", false);

  /**
   * Specifies the synthetic context of the startup runtime library. When this is set, the startup
   * runtime library will only be injected into the app when the synthetic context is in the
   * program. This can be used to avoid that the startup runtime library is injected multiple times
   * in presence of separate compilation.
   *
   * <p>Example synthetic context: "app.tivi.home.MainActivity".
   *
   * <p>Note that this is only meaningful when {@link #enableStartupInstrumentation} is set to true.
   */
  private String startupInstrumentationServerSyntheticContext =
      getSystemPropertyForDevelopment(
          "com.android.tools.r8.startup.instrumentationserversyntheticcontext");

  /**
   * Specifies the logcat tag that should be used by the InstrumentationServer when logging events.
   *
   * <p>When a logcat tag is not specified, the InstrumentationServer will not print events to
   * logcat. Instead, the startup events must be obtained by requesting the InstrumentationServer to
   * write the events to a file.
   */
  private String startupInstrumentationTag =
      getSystemPropertyForDevelopment("com.android.tools.r8.startup.instrumentationtag");

  private StartupConfiguration startupConfiguration;

  public boolean hasStartupInstrumentationServerSyntheticContext() {
    return startupInstrumentationServerSyntheticContext != null;
  }

  public String getStartupInstrumentationServerSyntheticContext() {
    return startupInstrumentationServerSyntheticContext;
  }

  public StartupOptions setStartupInstrumentationServerSyntheticContext(
      String startupInstrumentationServerSyntheticContext) {
    this.startupInstrumentationServerSyntheticContext =
        startupInstrumentationServerSyntheticContext;
    return this;
  }

  public boolean hasStartupInstrumentationTag() {
    return startupInstrumentationTag != null;
  }

  public String getStartupInstrumentationTag() {
    return startupInstrumentationTag;
  }

  public StartupOptions setStartupInstrumentationTag(String startupInstrumentationTag) {
    this.startupInstrumentationTag = startupInstrumentationTag;
    return this;
  }

  public boolean isMinimalStartupDexEnabled() {
    return enableMinimalStartupDex;
  }

  public StartupOptions setEnableMinimalStartupDex(boolean enableMinimalStartupDex) {
    this.enableMinimalStartupDex = enableMinimalStartupDex;
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

  public StartupOptions setStartupConfiguration(StartupConfiguration startupConfiguration) {
    this.startupConfiguration = startupConfiguration;
    return this;
  }
}
