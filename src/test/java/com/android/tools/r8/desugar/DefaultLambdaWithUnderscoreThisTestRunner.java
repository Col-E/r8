// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.JvmTestBuilder;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.desugar.DefaultLambdaWithUnderscoreThisTest.I;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultLambdaWithUnderscoreThisTestRunner extends DebugTestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DefaultLambdaWithUnderscoreThisTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  final Class<?> CLASS = DefaultLambdaWithUnderscoreThisTest.class;

  final String EXPECTED = StringUtils
      .lines("stateful(My _this variable foo Another ___this variable)");

  private void runDebugger(DebugTestConfig config, boolean desugared) throws Throwable {
    MethodReference main = Reference.methodFromMethod(CLASS.getMethod("main", String[].class));
    MethodReference stateful = Reference.methodFromMethod(I.class.getMethod("stateful"));
    Function<String, Command> checkThis =
        (String desugarThis) ->
            conditional(
                (state) ->
                    !desugared
                        ? Collections.singletonList(checkLocal("this"))
                        : ImmutableList.of(checkNoLocal("this"), checkLocal(desugarThis)));

    runDebugTest(
        config,
        CLASS,
        breakpoint(main, 22),
        run(),
        checkLine(22),
        stepInto(INTELLIJ_FILTER),
        checkLine(12),
        stepInto(INTELLIJ_FILTER),
        checkLine(13),
        // Desugaring will insert '__this' in place of 'this' here.
        checkThis.apply("__this"),
        checkLocal("_this"),
        breakpoint(main, 23),
        run(),
        checkLine(23),
        stepInto(INTELLIJ_FILTER),
        checkLine(14),
        stepInto(INTELLIJ_FILTER),
        checkLine(15),
        // Desugaring will insert '____this' in place of 'this' here.
        checkThis.apply("____this"),
        checkLocals("_this", "___this"),
        run());
  }

  @Test
  public void testJvm() throws Throwable {
    assumeTrue(parameters.isCfRuntime());
    JvmTestBuilder builder = testForJvm().addTestClasspath();
    builder.run(parameters.getRuntime(), CLASS).assertSuccessWithOutput(EXPECTED);
    runDebugger(builder.debugConfig(), false);
  }

  @Test
  public void testD8() throws Throwable {
    assumeTrue(parameters.isDexRuntime());
    D8TestCompileResult compileResult =
        testForD8()
            .addProgramClassesAndInnerClasses(CLASS)
            .setMinApiThreshold(AndroidApiLevel.K)
            .compile();
    compileResult
        // TODO(b/123506120): Add .assertNoMessages()
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED);
    runDebugger(compileResult.debugConfig(), true);
  }

  @Test
  public void testR8() throws Throwable {
    R8FullTestBuilder r8FullTestBuilder =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(CLASS)
            .addKeepAllClassesRule()
            .noMinification()
            .setMode(CompilationMode.DEBUG)
            .addOptionsModification(
                internalOptions -> {
                  if (parameters.isCfRuntime()) {
                    internalOptions.desugarState = DesugarState.ON;
                    internalOptions.cfToCfDesugar = true;
                  }
                });
    if (parameters.isDexRuntime()) {
      r8FullTestBuilder.setMinApiThreshold(AndroidApiLevel.K);
    }
    R8TestCompileResult compileResult = r8FullTestBuilder.compile();
    compileResult.run(parameters.getRuntime(), CLASS).assertSuccessWithOutput(EXPECTED);
    runDebugger(compileResult.debugConfig(), true);
  }
}
