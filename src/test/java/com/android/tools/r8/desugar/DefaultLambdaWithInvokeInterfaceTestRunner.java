// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultLambdaWithInvokeInterfaceTestRunner extends DebugTestBase {

  private final Class<?> CLASS = DefaultLambdaWithInvokeInterfaceTest.class;
  private final String EXPECTED = StringUtils.lines("stateful(hest)");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withApiLevel(AndroidApiLevel.K).build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8Cf() throws Throwable {
    parameters.assumeCfRuntime();
    R8TestCompileResult compileResult =
        testForR8(Backend.CF)
            .addProgramClassesAndInnerClasses(CLASS)
            .addDontObfuscate()
            .noTreeShaking()
            .debug()
            .compile();
    compileResult
        // TODO(b/123506120): Add .assertNoMessages()
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(inspector -> assertThat(inspector.clazz(CLASS), isPresent()));
    runDebugger(compileResult.debugConfig());
  }

  @Test
  public void testD8() throws Throwable {
    parameters.assumeDexRuntime();
    D8TestCompileResult compileResult =
        testForD8().addProgramClassesAndInnerClasses(CLASS).setMinApi(parameters).compile();
    compileResult
        // TODO(b/123506120): Add .assertNoMessages()
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(inspector -> assertThat(inspector.clazz(CLASS), isPresent()));
    runDebugger(compileResult.debugConfig());
  }

  private void runDebugger(DebugTestConfig config) throws Throwable {
    MethodReference main = Reference.methodFromMethod(CLASS.getMethod("main", String[].class));
    Command checkThis =
        conditional(
            (state) ->
                state.isCfRuntime()
                    ? Collections.singletonList(checkLocal("this"))
                    : ImmutableList.of(checkNoLocal("this"), checkLocal("_this")));

    runDebugTest(
        config,
        CLASS,
        breakpoint(main, 27),
        run(),
        checkLine(27),
        stepInto(INTELLIJ_FILTER),
        checkLine(18),
        checkThis,
        breakpoint(main, 28),
        run(),
        checkLine(28),
        checkLocal("stateful"),
        stepInto(INTELLIJ_FILTER),
        checkLine(19),
        checkThis,
        run());
  }
}
