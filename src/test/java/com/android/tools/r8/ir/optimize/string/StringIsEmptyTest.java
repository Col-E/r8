// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringIsEmptyTest extends TestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "false",
      "true",
      "false",
      "false"
  );
  private static final Class<?> MAIN = TestClass.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public StringIsEmptyTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue("Only run JVM reference on CF runtimes", parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void configure(InternalOptions options) {
    // This test wants to check if compile-time computation is not applied to non-null,
    // non-constant value. In a simple test setting, call-site optimization knows the argument is
    // always a non-null, specific constant, but that is beyond the scope of this test.
  }

  private void test(SingleTestRunResult result, int expectedStringIsEmptyCount) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(expectedStringIsEmptyCount, countCall(mainMethod, "String", "isEmpty"));

    MethodSubject wrapper = mainClass.uniqueMethodWithOriginalName("wrapper");
    assertThat(wrapper, isPresent());
    // Due to nullable, non-constant argument (w/o call-site optimization), isEmpty() should remain.
    assertEquals(1, countCall(wrapper, "String", "isEmpty"));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());

    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramClasses(MAIN)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 3);

    result =
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 1);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN)
            .enableProguardTestOptions()
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 1);
  }

  static class TestClass {

    @NeverInline
    static boolean wrapper(String arg) {
      // Cannot be computed at compile time (unless call-site optimization is enabled).
      return arg.isEmpty();
    }

    public static void main(String[] args) {
      String s1 = "non-empty";
      System.out.println(s1.isEmpty());
      String s2 = "";
      System.out.println(s2.isEmpty());
      System.out.println((s1 + s2).isEmpty());
      System.out.println(wrapper("non-null"));
      try {
        wrapper(null);
        throw new AssertionError("Should raise NullPointerException");
      } catch (NullPointerException npe) {
        // expected
      }
    }
  }

}
