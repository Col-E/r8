// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.methodparameters;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MethodParametersTest extends TestBase {

  private static String[] EXPECTED = {"hello", "darkness", "my", "old", "friend"};

  private final TestParameters parameters;
  private final boolean keepMethodParameters;

  @Parameters(name = "{0} methodparameters: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimes()
            .withDexRuntimesStartingFromExcluding(Version.V7_0_0)
            .build(),
        BooleanUtils.values());
  }

  public MethodParametersTest(TestParameters parameters, boolean keepMethodParameters) {
    this.parameters = parameters;
    this.keepMethodParameters = keepMethodParameters;
  }

  @Test
  public void testKeepingMethodParametersR8() throws Exception {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(getTransformedTestClass())
            .addKeepClassAndMembersRulesWithAllowObfuscation(TestClass.class)
            .addKeepMainRule(TestClass.class)
            .addKeepAttributeSourceFile()
            .applyIf(
                keepMethodParameters,
                builder -> builder.addKeepAttributes(ProguardKeepAttributes.METHOD_PARAMETERS))
            .setMinApi(keepMethodParameters ? AndroidApiLevel.O : AndroidApiLevel.L)
            // java.lang.reflect.Parameter was introduced in API level 26 (O).
            .addLibraryFiles(ToolHelper.getAndroidJar(apiLevelWithMethodParametersSupport()))
            .compile()
            .run(parameters.getRuntime(), TestClass.class);
    if (keepMethodParameters) {
      checkOutputContainsAll(runResult.getStdOut());
    } else {
      checkOutputNotContainsAll(runResult.getStdOut());
    }
  }

  @Test
  public void testKeepingMethodParameters()
      throws ExecutionException, CompilationFailedException, IOException {
    // In D8 we always output MethodParameters.
    assumeTrue(parameters.getBackend() == Backend.DEX);
    D8TestRunResult runResult =
        testForD8()
            .addProgramClassFileData(getTransformedTestClass())
            .setMinApi(keepMethodParameters ? AndroidApiLevel.O : AndroidApiLevel.L)
            .run(parameters.getRuntime(), TestClass.class);
    checkOutputContainsAll(runResult.getStdOut());
  }

  private void checkOutputContainsAll(String stdOut) {
    Arrays.asList(EXPECTED).forEach(expected -> assertThat(stdOut, containsString(expected)));
  }

  private void checkOutputNotContainsAll(String stdOut) {
    Arrays.asList(EXPECTED).forEach(expected -> assertThat(stdOut, not(containsString(expected))));
  }

  private byte[] getTransformedTestClass() throws IOException {
    return transformer(TestClass.class)
        .setMethodParameters(MethodPredicate.onName("main"), "hello")
        .setMethodParameters(MethodPredicate.onName("other"), "darkness", "my", "old", "friend")
        .transform();
  }

  public static class TestClass {

    public static void main(String[] hello) {
      for (Method method : TestClass.class.getMethods()) {
        for (Parameter parameter : method.getParameters()) {
          System.out.println(method.getName() + ": " + parameter.getName());
        }
      }
    }

    public void other(int darkness, String my, Object old, boolean friend) {
      // nothing to do, reflectively accessed...
    }
  }
}
