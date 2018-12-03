// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import java.util.function.BiConsumer;
import org.junit.Before;

public abstract class RetraceTestBase extends TestBase {
  protected Backend backend;
  protected CompilationMode mode;

  public RetraceTestBase(Backend backend, CompilationMode mode) {
    this.backend = backend;
    this.mode = mode;
  }

  public StackTrace expectedStackTrace;

  public abstract Class<?> getMainClass();

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForJvm()
            .addTestClasspath()
            .run(getMainClass())
            .assertFailure()
            .map(StackTrace::extractFromJvm);
  }

  public void runTest(String keepRule, BiConsumer<StackTrace, StackTrace> checker)
      throws Exception {

    R8TestRunResult result =
        testForR8(backend)
            .setMode(mode)
            .enableProguardTestOptions()
            .enableInliningAnnotations()
            .addProgramClasses(getMainClass())
            .addKeepMainRule(getMainClass())
            .addKeepRules(keepRule)
            .run(getMainClass())
            .assertFailure();

    // Extract actual stack trace and retraced stack trace from failed run result.
    StackTrace actualStackTrace = StackTrace.extractFromArt(result.getStdErr());
    StackTrace retracedStackTrace =
        actualStackTrace.retrace(result.proguardMap(), temp.newFolder().toPath());

    checker.accept(actualStackTrace, retracedStackTrace);
  }
}
