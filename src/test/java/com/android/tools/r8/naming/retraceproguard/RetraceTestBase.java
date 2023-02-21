// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retraceproguard;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.naming.retraceproguard.StackTrace.StackTraceLine;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.Before;

public abstract class RetraceTestBase extends TestBase {

  protected TestParameters parameters;
  protected CompilationMode mode;
  protected boolean compat;

  public RetraceTestBase(TestParameters parameters, CompilationMode mode, boolean compat) {
    this.parameters = parameters;
    this.mode = mode;
    this.compat = compat;
  }

  public StackTrace expectedStackTrace;

  public void configure(R8TestBuilder builder) {}

  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(getMainClass());
  }

  public abstract Class<?> getMainClass();

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForRuntime(parameters)
            .addProgramClasses(getClasses())
            .run(parameters.getRuntime(), getMainClass())
            .assertFailure()
            .map(StackTrace::extract);
  }

  public void runTest(List<String> keepRules, BiConsumer<StackTrace, StackTrace> checker)
      throws Exception {
    runTest(keepRules, checker, ThrowableConsumer.empty());
  }

  public void runTest(
      List<String> keepRules,
      BiConsumer<StackTrace, StackTrace> checker,
      ThrowableConsumer<R8TestCompileResult> compileResultConsumer)
      throws Exception {
    R8TestRunResult result =
        (compat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend()))
            .setMode(mode)
            .enableProguardTestOptions()
            .addProgramClasses(getClasses())
            .addKeepMainRule(getMainClass())
            .addKeepRules(keepRules)
            .setMinApi(parameters)
            .apply(this::configure)
            .compile()
            .apply(compileResultConsumer)
            .run(parameters.getRuntime(), getMainClass())
            .assertFailure();

    // Extract actual stack trace and retraced stack trace from failed run result.
    StackTrace actualStackTrace = StackTrace.extractFromArt(result.getStdErr());
    StackTrace retracedStackTrace =
        actualStackTrace.retrace(result.proguardMap(), temp.newFolder().toPath());

    checker.accept(actualStackTrace, retracedStackTrace);
  }

  protected boolean isNotDalvikNativeStartMethod(StackTraceLine retracedStackTraceLine) {
    return !(retracedStackTraceLine.className.equals("dalvik.system.NativeStart")
        && retracedStackTraceLine.methodName.equals("main"));
  }
}
