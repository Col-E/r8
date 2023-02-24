// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringForTesting.getCompanionClassNameSuffix;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.DebuggeeState;
import com.android.tools.r8.debug.DexDebugTestConfig;
import com.android.tools.r8.utils.AndroidApp;
import java.io.File;
import java.nio.file.Path;
import org.junit.rules.TemporaryFolder;

public class VerticalClassMergerDebugTestRunner extends DebugTestBase {

  private final String main;
  private final TemporaryFolder temp;

  private DebugTestRunner runner = null;

  public VerticalClassMergerDebugTestRunner(String main, TemporaryFolder temp) {
    this.main = main;
    this.temp = temp;
  }

  public void run(AndroidApp app, Path proguardMapPath) throws Throwable {
    Path appPath = File.createTempFile("app", ".zip", temp.getRoot()).toPath();
    app.writeToZipForTesting(appPath, OutputMode.DexIndexed);

    DexDebugTestConfig config = new DexDebugTestConfig(appPath);
    config.allowUnprocessedCommands();
    config.setProguardMap(proguardMapPath);

    this.runner =
        getDebugTestRunner(
            config, main, breakpoint(main, "main"), run(), stepIntoUntilNoLongerInApp());
    this.runner.runBare();
  }

  private void checkState(DebuggeeState state) {
    // If a class pkg.A is merged into pkg.B, and a method pkg.A.m() needs to be renamed, then
    // it will be renamed to pkg.B.m$pkg$A(). Since all tests are in the package "classmerging",
    // we check that no methods in the debugging state (i.e., after the Proguard map has been
    // applied) contain "$classmerging$.
    String qualifiedMethodSignature =
        state.getClassSignature() + "->" + state.getMethodName() + state.getMethodSignature();
    boolean holderIsCompanionClass = state.getClassName().endsWith(getCompanionClassNameSuffix());
    if (!holderIsCompanionClass) {
      assertThat(qualifiedMethodSignature, not(containsString("$classmerging$")));
    }
  }

  // Keeps stepping in until it is no longer in a class from the classmerging package.
  // Then starts stepping out until it is again in the classmerging package.
  private Command stepIntoUntilNoLongerInApp() {
    return stepUntil(
        StepKind.INTO,
        StepLevel.INSTRUCTION,
        state -> {
          if (state.getClassSignature().contains("classmerging")) {
            checkState(state);

            // Continue stepping into.
            return false;
          }

          // Stop stepping into.
          runner.enqueueCommandFirst(stepOutUntilInApp());
          return true;
        });
  }

  // Keeps stepping out until it is in a class from the classmerging package.
  // Then starts stepping in until it is no longer in the classmerging package.
  private Command stepOutUntilInApp() {
    return stepUntil(
        StepKind.OUT,
        StepLevel.INSTRUCTION,
        state -> {
          if (state.getClassSignature().contains("classmerging")) {
            checkState(state);

            // Stop stepping out.
            runner.enqueueCommandFirst(stepIntoUntilNoLongerInApp());
            return true;
          }

          // Continue stepping out.
          return false;
        });
  }
}
