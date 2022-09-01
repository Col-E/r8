// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.FrameInspector;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LoadInvokeLoadOptimizationTestRunner extends DebugTestBase {

  static final Class CLASS = LoadInvokeLoadOptimizationTest.class;
  static final String NAME = CLASS.getCanonicalName();
  static final String FILE = CLASS.getSimpleName() + ".java";
  static final AndroidApiLevel minApi = AndroidApiLevel.B;
  static final String EXPECTED = "";

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection setup() {
    return TestParameters.builder().withAllRuntimes().build();
  }

  public LoadInvokeLoadOptimizationTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Throwable {
    assumeTrue(parameters.isCfRuntime());
    testForJvm(temp)
        .addProgramClasses(CLASS)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .debugger(this::runDebugger);
  }

  @Test
  public void testD8() throws Throwable {
    testForD8(parameters.getBackend())
        .setMinApi(minApi)
        .addProgramClasses(CLASS)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .debugger(this::runDebugger);
  }

  @Test
  public void testR8() throws Throwable {
    testForR8(parameters.getBackend())
        .noTreeShaking()
        .addDontObfuscate()
        .addKeepRules("-keepattributes SourceFile,LineNumberTable")
        .addProgramClasses(CLASS)
        .setMinApi(minApi)
        .debug()
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .debugger(this::runDebugger);
  }

  public void runDebugger(DebugTestConfig config) throws Throwable {
    Value int42 = Value.createInt(42);
    Value int7 = Value.createInt(7);
    // The test ensures that when breaking inside a function and changing a local in the parent
    // frame, that the new value is passed to the second invocation of the function.
    // This ensures that no peephole optimizations will optimize if there is any debug information.
    runDebugTest(
        config,
        NAME,
        breakpoint(NAME, "bar"),
        run(),
        checkLine(FILE, 10),
        checkLocal("x", int42),
        inspect(
            t -> {
              FrameInspector frame = t.getFrame(1);
              frame.checkLine(FILE, 13);
              frame.checkLocal("x", int42);
              frame.setLocal("x", int7);
            }),
        run(),
        checkLine(FILE, 10),
        checkLocal("x", int7),
        run());
  }
}
