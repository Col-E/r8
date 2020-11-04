// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class ForNameTestMain {
  static class A {
    static {
      System.out.println("A#<clinit>");
    }
  }

  static class B {
    static {
      System.out.println("B#<clinit>");
    }
  }

  static class ArrayBase {
    static {
      System.out.println("ArrayBase#<clinit>");
    }
  }

  public static void main(String[] args) throws Exception {
    {
      try {
        // Not found, hence kept.
        Class<?> c = Class.forName("UnknownClass");
        throw new AssertionError("Should preserve ClassNotFoundException.");
      } catch (ClassNotFoundException e) {
        // Expected
        System.out.println("Unknown");
      }
    }
    {
      A a = new A();
      // initialized, should be rewritten to const-class.
      Class<?> c = Class.forName("com.android.tools.r8.ir.optimize.reflection.ForNameTestMain$A");
      System.out.println(c.getSimpleName());
      // Not initialized, hence kept.
      c = Class.forName("com.android.tools.r8.ir.optimize.reflection.ForNameTestMain$B");
      System.out.println(c.getSimpleName());
      // But, array is okay even though the base type is not initialized yet.
      c = Class.forName("[Lcom.android.tools.r8.ir.optimize.reflection.ForNameTestMain$ArrayBase;");
      System.out.println(c.getSimpleName());
    }
  }
}

@RunWith(Parameterized.class)
public class ForNameTest extends ReflectionOptimizerTestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "Unknown",
      "A#<clinit>",
      "A",
      "B#<clinit>",
      "B",
      "ArrayBase[]"
  );
  private static final Class<?> MAIN = ForNameTestMain.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public ForNameTest(TestParameters parameters) {
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

  private void test(
      SingleTestRunResult<?> result, int expectedForNameCount, int expectedConstClassCount)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(expectedForNameCount, countForName(mainMethod));
    assertEquals(expectedForNameCount + 2, countConstString(mainMethod));
    assertEquals(expectedConstClassCount, countConstClass(mainMethod));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());

    // D8 debug.
    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramClassesAndInnerClasses(MAIN)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 4, 0);

    // D8 release.
    result =
        testForD8()
            .release()
            .addProgramClassesAndInnerClasses(MAIN)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 4, 0);
  }

  @Test
  public void testR8() throws Exception {
    // R8 debug, no minification.
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .debug()
            .addProgramClassesAndInnerClasses(MAIN)
            .addKeepMainRule(MAIN)
            .addKeepAllClassesRule()
            .addKeepAttributes("EnclosingMethod", "InnerClasses")
            .noMinification()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), MAIN);
    test(result, 4, 0);

    // R8 release, no minification.
    result =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(MAIN)
            .addKeepMainRule(MAIN)
            .addKeepAllClassesRule()
            .addKeepAttributes("EnclosingMethod", "InnerClasses")
            .noMinification()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 1, 3);

    // R8 release, minification.
    result =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(MAIN)
            .addKeepMainRule(MAIN)
            .addKeepAllClassesRuleWithAllowObfuscation()
            .addKeepAttributes("EnclosingMethod", "InnerClasses")
            .setMinApi(parameters.getApiLevel())
            // We are not checking output because it can't be matched due to minification. Just run.
            .run(parameters.getRuntime(), MAIN);
    test(result, 1, 3);
  }
}
