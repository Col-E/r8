// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringToStringTest extends TestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "constant-x",
      "R8"
  );
  private static final Class<?> MAIN = TestClass.class;
  private static final String STRING_DESCRIPTOR = "Ljava/lang/String;";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testJVMOutput() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private static boolean isStringToString(DexMethod method) {
    return method.holder.toDescriptorString().equals(STRING_DESCRIPTOR)
        && method.getArity() == 0
        && method.proto.returnType.toDescriptorString().equals(STRING_DESCRIPTOR)
        && method.name.toString().equals("toString");
  }

  private long countStringToString(MethodSubject method) {
    return method.streamInstructions().filter(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isStringToString(instructionSubject.getMethod());
      }
      return false;
    }).count();
  }

  private void test(SingleTestRunResult<?> result, int expectedStringToStringCount)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(expectedStringToStringCount, countStringToString(mainMethod));
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();

    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 1);

    result =
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .setMinApi(parameters)
            .addDontObfuscate()
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0);
  }

  static class TestClass {

    @NeverInline
    static String hideNPE(String s) {
      return s.toString();
    }

    public static void main(String[] args) {
      String x = "constant-x";
      System.out.println(x.toString());
      StringBuilder builder = new StringBuilder();
      builder.append("R");
      builder.append(8);
      System.out.println(builder.toString());
      try {
        System.out.println(hideNPE(null));
        throw new AssertionError("Expected NullPointerException");
      } catch (NullPointerException npe) {
        // Expected
      }
    }
  }
}
