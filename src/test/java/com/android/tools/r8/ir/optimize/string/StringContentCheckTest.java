// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
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
public class StringContentCheckTest extends TestBase {
  private static final String STRING_DESCRIPTOR = "Ljava/lang/String;";
  private static final String JAVA_OUTPUT = StringUtils.lines(
      // s1, contains(String)
      "true",
      // s1, startsWith(String)
      "true",
      // s1, endsWith(String)
      "true",
      // s1, equals(String)
      "true",
      // s1, equalsIgnoreCase(String)
      "true",
      // s1, contentEquals(CharSequence)
      "true",
      // s1, contentEquals(StringBuffer)
      "true",
      // s1, indexOf(int)
      "3",
      // s1, indexOf(String)
      "4",
      // s1, lastIndexOf(int)
      "16",
      // s1, lastIndexOf(String)
      "17",
      // s1, compareTo(String)
      "true",
      // s1, compareToIgnoreCase(String)
      "true",
      // s1, substring(int)
      "prefix",
      // s1, substring(int, int)
      "suffix",
      // s1, substring(int, int)
      "-suffix",
      // s2, contains(String)
      "false",
      // s2, startsWith(String)
      "false",
      // s2, endsWith(String)
      "false",
      // s2, equals(String)
      "false",
      // s2, equalsIgnoreCase(String)
      "false",
      // s2, contentEquals(CharSequence)
      "false",
      // s2, contentEquals(StringBuffer)
      "false",
      // s2, indexOf(int)
      "-1",
      // s2, indexOf(String)
      "-1",
      // s2, lastIndexOf(int)
      "-1",
      // s2, lastIndexOf(String)
      "-1",
      // s2, compareTo(String)
      "false",
      // s2, compareToIgnoreCase(String)
      "false",
      // s2, substring(int, int)
      "SUFFIX",
      // argCouldBeNull
      "false"
  );
  private static final Class<?> MAIN = TestClass.class;

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

  private static boolean isStringContentChecker(DexMethod method) {
    return method.holder.toDescriptorString().equals(STRING_DESCRIPTOR)
        && (method.proto.returnType.isBooleanType()
            || method.proto.returnType.isIntType()
            || method.proto.returnType.toDescriptorString().equals(STRING_DESCRIPTOR))
        && (method.name.toString().equals("contains")
            || method.name.toString().equals("startsWith")
            || method.name.toString().equals("endsWith")
            || method.name.toString().equals("equals")
            || method.name.toString().equals("equalsIgnoreCase")
            || method.name.toString().equals("contentEquals")
            || method.name.toString().equals("indexOf")
            || method.name.toString().equals("lastIndexOf")
            || method.name.toString().equals("compareTo")
            || method.name.toString().equals("compareToIgnoreCase")
            || method.name.toString().equals("substring"));
  }

  private long countStringContentChecker(MethodSubject method) {
    return method.streamInstructions().filter(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isStringContentChecker(instructionSubject.getMethod());
      }
      return false;
    }).count();
  }

  private void test(SingleTestRunResult<?> result, int expectedStringContentCheckerCount)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    long count = countStringContentChecker(mainMethod);
    assertEquals(expectedStringContentCheckerCount, count);

    MethodSubject argCouldBeNull = mainClass.uniqueMethodWithOriginalName("argCouldBeNull");
    assertThat(argCouldBeNull, isPresent());
    // Because of nullable argument, all checkers should remain.
    assertEquals(10, countStringContentChecker(argCouldBeNull));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());

    SingleTestRunResult<?> result =
        testForD8()
            .debug()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 31);

    result =
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 16);
  }

  @Test
  public void testR8() throws Exception {
    SingleTestRunResult<?> result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 16);
  }

  static class TestClass {

    @NeverInline
    static boolean argCouldBeNull(String arg) {
      return "CONST".contains(arg)
          && "prefix".startsWith(arg)
          && "suffix".endsWith(arg)
          && "CONST".equals(arg)
          && "CONST".equalsIgnoreCase(arg)
          && "CONST".contentEquals(arg)
          && "CONST".indexOf(arg) > 0
          && "CONST".lastIndexOf(arg) > 0
          && "CONST".compareTo(arg) > 0
          && "CONST".compareToIgnoreCase(arg) > 0;
    }

    public static void main(String[] args) {
      {
        String s1 = "prefix-CONST-suffix";
        System.out.println(s1.contains("CONST"));
        System.out.println(s1.startsWith("prefix"));
        System.out.println(s1.endsWith("suffix"));
        System.out.println(s1.equals("prefix-CONST-suffix"));
        System.out.println(s1.equalsIgnoreCase("PREFIX-const-SUFFIX"));
        System.out.println(s1.contentEquals("prefix-CONST-suffix"));
        System.out.println(s1.contentEquals(new StringBuffer("prefix-CONST-suffix")));
        System.out.println(s1.indexOf('f'));
        System.out.println(s1.indexOf("ix"));
        System.out.println(s1.lastIndexOf('f'));
        System.out.println(s1.lastIndexOf("ix"));
        System.out.println(s1.compareTo("prefix-CONST-suffix") == 0);
        System.out.println(s1.compareToIgnoreCase("PREFIX-const-SUFFIX") == 0);
        // "prefix" exists
        System.out.println(s1.substring(0, 6));
        // "suffix" exists
        System.out.println(s1.substring(13));
        // "-suffix" doesn't.
        System.out.println(s1.substring(12));
      }

      {
        // Not compile-time constant string
        String s2 = args.length > 8 ? "prefix-CONST-suffix" : "PREFIX-const-SUFFIX";
        System.out.println(s2.contains("CONST"));
        System.out.println(s2.startsWith("prefix"));
        System.out.println(s2.endsWith("suffix"));
        System.out.println(s2.equals("prefix-CONST-suffix"));
        System.out.println(s2.equalsIgnoreCase("pre-con-suf"));
        System.out.println(s2.contentEquals("prefix-CONST-suffix"));
        System.out.println(s2.contentEquals(new StringBuffer("prefix-CONST-suffix")));
        System.out.println(s2.indexOf('f'));
        System.out.println(s2.indexOf("ix"));
        System.out.println(s2.lastIndexOf('f'));
        System.out.println(s2.lastIndexOf("ix"));
        System.out.println(s2.compareTo("prefix-CONST-suffix") == 0);
        System.out.println(s2.compareToIgnoreCase("pre-con-suf") == 0);
        System.out.println(s2.substring(13));
      }

      {
        System.out.println(argCouldBeNull("prefix-CONST-suffix"));
        try {
          argCouldBeNull(null);
          throw new AssertionError("Should raise NullPointerException");
        } catch (NullPointerException npe) {
          // expected
        }
      }

      {
        try {
          System.out.println("qwerty".substring(8));
          throw new AssertionError("Should raise StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException e) {
          // expected
        }
      }
    }
  }
}
