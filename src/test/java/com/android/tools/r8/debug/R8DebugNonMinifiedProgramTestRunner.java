// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.function.BiFunction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class R8DebugNonMinifiedProgramTestRunner extends DebugTestBase {

  private static final Class<?> CLASS = R8DebugNonMinifiedProgramTest.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public R8DebugNonMinifiedProgramTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static BiFunction<Backend, AndroidApiLevel, R8TestCompileResult> compiledDebug
      = memoizeBiFunction(R8DebugNonMinifiedProgramTestRunner::compileDebug);

  private static BiFunction<Backend, AndroidApiLevel, R8TestCompileResult> compiledNoOptNoMinify
      = memoizeBiFunction(R8DebugNonMinifiedProgramTestRunner::compileNoOptNoMinify);

  private static R8TestCompileResult compileDebug(Backend backend, AndroidApiLevel apiLevel)
      throws Exception {
    return compile(testForR8(getStaticTemp(), backend).debug(), apiLevel);
  }

  private static R8TestCompileResult compileNoOptNoMinify(Backend backend, AndroidApiLevel apiLevel)
      throws Exception {
    return compile(
        testForR8(getStaticTemp(), backend)
            .addKeepRules("-dontoptimize", "-dontobfuscate", "-keepattributes LineNumberTable"),
        apiLevel);
  }

  private static R8TestCompileResult compile(R8FullTestBuilder builder, AndroidApiLevel apiLevel)
      throws Exception {
    return builder
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .addProgramClassesAndInnerClasses(CLASS)
        .addKeepMainRule(CLASS)
        .setMinApi(apiLevel)
        .compile()
        .inspect(
            inspector -> {
              // Check that tree shaking is running (e.g., B is removed).
              assertTrue(inspector.clazz(R8DebugNonMinifiedProgramTest.A.class).isPresent());
              assertFalse(inspector.clazz(R8DebugNonMinifiedProgramTest.B.class).isPresent());
            });
  }

  @Test
  public void testDebugMode() throws Throwable {
    runTest(compiledDebug.apply(parameters.getBackend(), parameters.getApiLevel()));
  }

  @Test
  public void testNoOptimizationAndNoMinification() throws Throwable {
    runTest(compiledNoOptNoMinify.apply(parameters.getBackend(), parameters.getApiLevel()));
  }

  private void runTest(R8TestCompileResult compileResult) throws Throwable {
    compileResult
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutputLines("Hello, world: Class A");

    DebugTestConfig debugTestConfig = compileResult.debugConfig();
    assertNull("For this test the map file must not be present!", debugTestConfig.getProguardMap());
    runDebugTest(debugTestConfig, CLASS,
        breakpoint(CLASS.getTypeName(), "main"),
        run(),
        checkLine(12),
        run());
  }
}