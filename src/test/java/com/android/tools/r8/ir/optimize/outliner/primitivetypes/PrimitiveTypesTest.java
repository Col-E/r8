// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner.primitivetypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PrimitiveTypesTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public PrimitiveTypesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void validateOutlining(CodeInspector inspector, Class<?> testClass, String argumentType) {
    ClassSubject outlineClass = inspector.clazz(OutlineOptions.CLASS_NAME);
    MethodSubject outline0Method =
        outlineClass.method(
            "java.lang.String", "outline0", ImmutableList.of(argumentType, argumentType));
    assertThat(outline0Method, isPresent());
    ClassSubject classSubject = inspector.clazz(testClass);
    assertThat(
        classSubject.uniqueMethodWithName("method1"), CodeMatchers.invokesMethod(outline0Method));
    assertThat(
        classSubject.uniqueMethodWithName("method2"), CodeMatchers.invokesMethod(outline0Method));
  }

  public void runTest(Class<?> testClass, String argumentType, String expectedOutput)
      throws Exception {
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .enableClassInliningAnnotations()
        .addProgramClasses(testClass)
        .addProgramClasses(MyStringBuilder.class)
        .addKeepMainRule(testClass)
        .setMinApi(parameters.getRuntime())
        .noMinification()
        .addOptionsModification(
            options -> {
              if (parameters.isCfRuntime()) {
                assert !options.outline.enabled;
                options.outline.enabled = true;
              }
              options.outline.threshold = 2;
              options.outline.minSize = 2;
            })
        .compile()
        .inspect(inspector -> validateOutlining(inspector, testClass, argumentType))
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void testBoolean() throws Exception {
    runTest(TestClassBoolean.class, "boolean", StringUtils.lines("truetrue", "falsefalse"));
  }

  @Test
  public void testShort() throws Exception {
    runTest(TestClassShort.class, "short", StringUtils.lines("11", "22"));
  }

  @Test
  public void testByte() throws Exception {
    runTest(TestClassByte.class, "byte", StringUtils.lines("33", "44"));
  }

  @Test
  public void testChar() throws Exception {
    runTest(TestClassChar.class, "char", StringUtils.lines("AA", "BB"));
  }

  // StringBuilder wrapper for testing, as StringBuilder does not have append methods with
  // byte or short but only int.
  @NeverClassInline
  public static class MyStringBuilder {
    private final StringBuilder sb = new StringBuilder();

    @NeverInline
    public MyStringBuilder append(byte b) {
      sb.append(b);
      return this;
    }

    @NeverInline
    public MyStringBuilder append(short s) {
      sb.append(s);
      return this;
    }

    @NeverInline
    public String toString() {
      return sb.toString();
    }
  }

  static class TestClassBoolean {

    @NeverInline
    public static String method1(boolean b) {
      StringBuilder sb = new StringBuilder();
      sb.append(b);
      sb.append(b);
      return sb.toString();
    }

    @NeverInline
    public static String method2(boolean b) {
      StringBuilder sb = new StringBuilder();
      sb.append(b);
      sb.append(b);
      return sb.toString();
    }

    public static void main(String[] args) {
      System.out.println(method1(true));
      System.out.println(method2(false));
    }
  }

  static class TestClassByte {

    @NeverInline
    public static String method1(byte b) {
      MyStringBuilder sb = new MyStringBuilder();
      sb.append(b);
      sb.append(b);
      return sb.toString();
    }

    @NeverInline
    public static String method2(byte b) {
      MyStringBuilder sb = new MyStringBuilder();
      sb.append(b);
      sb.append(b);
      return sb.toString();
    }

    public static void main(String[] args) {
      System.out.println(method1((byte) 3));
      System.out.println(method2((byte) 4));
    }
  }

  static class TestClassShort {

    @NeverInline
    public static String method1(short s) {
      MyStringBuilder sb = new MyStringBuilder();
      sb.append(s);
      sb.append(s);
      return sb.toString();
    }

    @NeverInline
    public static String method2(short s) {
      MyStringBuilder sb = new MyStringBuilder();
      sb.append(s);
      sb.append(s);
      return sb.toString();
    }

    public static void main(String[] args) {
      System.out.println(method1((short) 1));
      System.out.println(method2((short) 2));
    }
  }

  static class TestClassChar {

    @NeverInline
    public static String method1(char c) {
      StringBuilder sb = new StringBuilder();
      sb.append(c);
      sb.append(c);
      return sb.toString();
    }

    @NeverInline
    public static String method2(char c) {
      StringBuilder sb = new StringBuilder();
      sb.append(c);
      sb.append(c);
      return sb.toString();
    }

    public static void main(String[] args) {
      System.out.println(method1('A'));
      System.out.println(method2('B'));
    }
  }
}
