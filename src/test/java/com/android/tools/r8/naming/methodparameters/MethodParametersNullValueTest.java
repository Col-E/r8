// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.methodparameters;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.StringUtils;
import java.lang.reflect.MalformedParametersException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Regression test for b/281536562 */
@RunWith(Parameterized.class)
public class MethodParametersNullValueTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("bar", "arg1", "baz");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public MethodParametersNullValueTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(getTransformedTestClass())
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkExpected);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getTransformedTestClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkExpected);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    // Don't run on old API as that build is "ill configured" and triggers missing type refs.
    assumeTrue(
        parameters.isCfRuntime()
            || parameters
                .getApiLevel()
                .isGreaterThanOrEqualTo(apiLevelWithMethodParametersSupport()));
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedTestClass())
        .setMinApi(parameters)
        .addKeepClassAndMembersRules(TestClass.class)
        .addKeepAllAttributes()
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkExpected);
  }

  private void checkExpected(TestRunResult<?> result) {
    if (parameters.isCfRuntime(CfVm.JDK8)) {
      // JDK8 will throw when accessing a null valued method parameter name.
      result.assertFailureWithErrorThatThrows(MalformedParametersException.class);
    } else if (parameters.isDexRuntimeVersionOlderThanOrEqual(Version.V7_0_0)) {
      // API 26 introduced the java.lang.reflect.Parameter and methods.
      result.assertFailureWithErrorThatThrows(NoSuchMethodError.class);
    } else {
      result.assertSuccessWithOutput(EXPECTED);
    }
  }

  private byte[] getTransformedTestClass() throws Exception {
    return transformer(TestClass.class)
        .setMethodParameters(MethodPredicate.onName("foo"), "bar", null, "baz")
        .transform();
  }

  static class TestClass {

    public static void foo(String bar, String willBeNull, String baz) {
      System.out.println("" + bar + willBeNull + baz);
    }

    public static void main(String[] args) {
      for (Method method : TestClass.class.getMethods()) {
        if (method.getName().equals("foo")) {
          for (Parameter parameter : method.getParameters()) {
            System.out.println(parameter.getName());
          }
        }
      }
    }
  }
}
