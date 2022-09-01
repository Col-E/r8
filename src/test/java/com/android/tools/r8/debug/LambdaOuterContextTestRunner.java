// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.LambdaOuterContextTest.Converter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaOuterContextTestRunner extends DebugTestBase {

  public static final Class<?> CLASS = LambdaOuterContextTest.class;
  public static final String EXPECTED = StringUtils.lines("84");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private final TestParameters parameters;

  public LambdaOuterContextTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Throwable {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClassesAndInnerClasses(CLASS)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .debugger(this::runDebugger);
  }

  @Test
  public void testD8() throws Throwable {
    testForD8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(CLASS)
        .setMinApi(AndroidApiLevel.B)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .debugger(this::runDebugger);
  }

  @Test
  public void testR8() throws Throwable {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(CLASS)
        .debug()
        .addDontObfuscate()
        .noTreeShaking()
        .setMinApi(AndroidApiLevel.B)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .debugger(this::runDebugger);
  }

  private void runDebugger(DebugTestConfig config) throws Throwable {
    runDebugTest(
        config,
        CLASS,
        breakpoint(methodFromMethod(CLASS.getMethod("foo", Converter.class))),
        run(),
        checkLine(19),
        checkLocals("this", "converter"),
        checkFieldOnThis("outer", null, Value.createInt(42)),
        stepInto(INTELLIJ_FILTER),
        checkLine(25),
        checkLocals("this", "value", "arg"),
        checkNoLocal("outer"),
        checkFieldOnThis("outer", null, Value.createInt(42)),
        run());
  }
}
