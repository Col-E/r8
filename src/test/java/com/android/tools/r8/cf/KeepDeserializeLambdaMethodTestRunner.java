// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8CompatTestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepDeserializeLambdaMethodTestRunner extends TestBase {

  private static final Class TEST_CLASS_CF =
      com.android.tools.r8.cf.KeepDeserializeLambdaMethodTestCf.class;
  private static final Class TEST_CLASS_DEX =
      com.android.tools.r8.cf.KeepDeserializeLambdaMethodTestDex.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withCfRuntimes().withDexRuntime(Version.last()).build();
  }

  private final TestParameters parameters;

  public KeepDeserializeLambdaMethodTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNoTreeShakingCf() throws Exception {
    test(false);
  }

  @Test
  public void testKeepRuleCf() throws Exception {
    // Only run for CF runtimes, since the keep rule does not match anything when compiling for the
    // DEX.
    assumeTrue(parameters.isCfRuntime());
    test(true);
  }

  private void test(boolean keepRule)
      throws IOException, CompilationFailedException, ExecutionException {
    Class testClass = parameters.isCfRuntime() ? TEST_CLASS_CF : TEST_CLASS_DEX;
    R8CompatTestBuilder builder =
        testForR8Compat(parameters.getBackend())
            .addProgramClasses(
                com.android.tools.r8.cf.KeepDeserializeLambdaMethodTest.class, testClass)
            .setMinApi(parameters.getRuntime())
            .addKeepMainRule(testClass)
            .noMinification();
    if (keepRule) {
      builder.addKeepRules(
          "-keepclassmembers class * {",
          "private static synthetic java.lang.Object "
              + "$deserializeLambda$(java.lang.invoke.SerializedLambda);",
          "}");
    } else {
      builder.noTreeShaking();
    }
    R8TestRunResult result =
        builder
            .run(parameters.getRuntime(), testClass)
            .assertSuccessWithOutputThatMatches(
                containsString(KeepDeserializeLambdaMethodTest.LAMBDA_MESSAGE))
            .inspect(
                inspector -> {
                  MethodSubject method =
                      inspector.clazz(testClass).uniqueMethodWithName("$deserializeLambda$");
                  assertEquals(parameters.isCfRuntime(), method.isPresent());
                });
  }
}
