// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static com.android.tools.r8.references.Reference.methodFromMethod;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.JvmTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.debug.LambdaOuterContextTest.Converter;
import com.android.tools.r8.utils.StringUtils;
import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Ignore;
import org.junit.Test;

public class LambdaOuterContextTestRunner extends DebugTestBase {

  public static final Class<?> CLASS = LambdaOuterContextTest.class;
  public static final String EXPECTED = StringUtils.lines("84");

  @Test
  public void testJvm() throws Throwable {
    JvmTestBuilder jvmTestBuilder = testForJvm().addTestClasspath();
    jvmTestBuilder.run(CLASS).assertSuccessWithOutput(EXPECTED);
    runDebugger(jvmTestBuilder.debugConfig());
  }

  @Test
  public void testD8() throws Throwable {
    D8TestCompileResult compileResult =
        testForD8().addProgramClassesAndInnerClasses(CLASS).compile();
    compileResult.run(CLASS).assertSuccessWithOutput(EXPECTED);
    runDebugger(compileResult.debugConfig());
  }

  @Test
  public void testR8Cf() throws Throwable {
    R8TestCompileResult compileResult =
        testForR8(Backend.CF)
            .addProgramClassesAndInnerClasses(CLASS)
            .debug()
            .noMinification()
            .noTreeShaking()
            .compile();
    compileResult.run(CLASS).assertSuccessWithOutput(EXPECTED);
    runDebugger(compileResult.debugConfig());
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
