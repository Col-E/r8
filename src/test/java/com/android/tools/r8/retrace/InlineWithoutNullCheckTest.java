// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileNameAndLineNumber;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InlineWithoutNullCheckTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public InlineWithoutNullCheckTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public StackTrace expectedStackTraceForInlineMethod;
  public StackTrace expectedStackTraceForInlineField;
  public StackTrace expectedStackTraceForInlineStaticField;

  @Before
  public void setup() throws Exception {
    // Get the expected stack traces by running on the runtime to test.
    expectedStackTraceForInlineMethod =
        testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
            .addInnerClasses(InlineWithoutNullCheckTest.class)
            .run(parameters.getRuntime(), TestClassForInlineMethod.class)
            .writeProcessResult(System.out)
            .assertFailure()
            .getStackTrace();
    expectedStackTraceForInlineField =
        testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
            .addInnerClasses(InlineWithoutNullCheckTest.class)
            .run(parameters.getRuntime(), TestClassForInlineField.class)
            .writeProcessResult(System.out)
            .assertFailure()
            .getStackTrace();
    expectedStackTraceForInlineStaticField =
        testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
            .addInnerClasses(InlineWithoutNullCheckTest.class)
            .run(parameters.getRuntime(), TestClassForInlineStaticField.class)
            .writeProcessResult(System.out)
            .assertFailure()
            .getStackTrace();

    // Check the expected stack traces from running on the runtime to test.
    assertThat(
        expectedStackTraceForInlineMethod,
        isSameExceptForFileNameAndLineNumber(
            StackTrace.builder()
                .addWithoutFileNameAndLineNumber(A.class, "inlineMethodWhichAccessInstanceMethod")
                .addWithoutFileNameAndLineNumber(TestClassForInlineMethod.class, "main")
                .build()));
    assertThat(
        expectedStackTraceForInlineField,
        isSameExceptForFileNameAndLineNumber(
            StackTrace.builder()
                .addWithoutFileNameAndLineNumber(A.class, "inlineMethodWhichAccessInstanceField")
                .addWithoutFileNameAndLineNumber(TestClassForInlineField.class, "main")
                .build()));
    assertThat(
        expectedStackTraceForInlineStaticField,
        isSameExceptForFileNameAndLineNumber(
            StackTrace.builder()
                .addWithoutFileNameAndLineNumber(A.class, "inlineMethodWhichAccessStaticField")
                .addWithoutFileNameAndLineNumber(TestClassForInlineStaticField.class, "main")
                .build()));
  }

  private void checkSomething(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(Result.class);
    assertThat(classSubject, isPresent());
    assertThat(
        classSubject.uniqueMethodWithOriginalName("methodWhichAccessInstanceMethod"),
        not(isPresent()));
    assertThat(
        classSubject.uniqueMethodWithOriginalName("methodWhichAccessInstanceField"),
        not(isPresent()));
    assertThat(
        classSubject.uniqueMethodWithOriginalName("methodWhichAccessStaticField"),
        not(isPresent()));
  }

  @Test
  public void testInlineMethodWhichChecksNullReceiverBeforeAnySideEffectMethod() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InlineWithoutNullCheckTest.class)
        .addKeepMainRule(TestClassForInlineMethod.class)
        .enableAlwaysInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::checkSomething)
        .run(parameters.getRuntime(), TestClassForInlineMethod.class)
        .assertFailure()
        .inspectStackTrace(
            stackTrace -> assertThat(expectedStackTraceForInlineMethod, isSame(stackTrace)));
  }

  @Test
  public void testInlineMethodWhichChecksNullReceiverBeforeAnySideEffectField() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InlineWithoutNullCheckTest.class)
        .addKeepMainRule(TestClassForInlineField.class)
        .enableAlwaysInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::checkSomething)
        .run(parameters.getRuntime(), TestClassForInlineField.class)
        .assertFailure()
        .inspectStackTrace(
            stackTrace -> assertThat(expectedStackTraceForInlineField, isSame(stackTrace)));
  }

  @Test
  public void testInlineMethodWhichChecksNullReceiverBeforeAnySideEffectStaticField()
      throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InlineWithoutNullCheckTest.class)
        .addKeepMainRule(TestClassForInlineStaticField.class)
        .enableAlwaysInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::checkSomething)
        .run(parameters.getRuntime(), TestClassForInlineStaticField.class)
        .assertFailure()
        .inspectStackTrace(
            stackTrace -> assertThat(expectedStackTraceForInlineStaticField, isSame(stackTrace)));
  }

  private StackTrace.Builder createStackTraceBuilder() {
    StackTrace.Builder builder = StackTrace.builder();
    if (canUseRequireNonNull()) {
      if (parameters.isCfRuntime()
          && parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK9)) {
        builder.addWithoutFileNameAndLineNumber("java.base/java.util.Objects", "requireNonNull");
      } else {
        builder.addWithoutFileNameAndLineNumber(Objects.class, "requireNonNull");
      }
    }
    return builder;
  }

  private boolean canUseRequireNonNull() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }

  static class TestClassForInlineMethod {

    public static void main(String[] args) {
      A a = new A(System.currentTimeMillis() > 0 ? null : new Result());
      System.out.print(a.inlineMethodWhichAccessInstanceMethod());
    }
  }

  static class TestClassForInlineField {

    public static void main(String[] args) {
      A a = new A(System.currentTimeMillis() > 0 ? null : new Result());
      System.out.print(a.inlineMethodWhichAccessInstanceField());
    }
  }

  static class TestClassForInlineStaticField {

    public static void main(String[] args) {
      A a = new A(System.currentTimeMillis() > 0 ? null : new Result());
      System.out.print(a.inlineMethodWhichAccessStaticField());
    }
  }

  static class A {
    Result result;

    A(Result result) {
      this.result = result;
    }

    @NeverInline
    Result get() {
      return result;
    }

    @NeverInline
    int inlineMethodWhichAccessInstanceMethod() {
      return get().methodWhichAccessInstanceMethod();
    }

    @NeverInline
    int inlineMethodWhichAccessInstanceField() {
      return get().methodWhichAccessInstanceField();
    }

    @NeverInline
    int inlineMethodWhichAccessStaticField() {
      return get().methodWhichAccessStaticField();
    }
  }

  static class Result {
    int x = 1;
    static int y = 1;

    @AlwaysInline
    int methodWhichAccessInstanceMethod() {
      return privateMethod();
    }

    @AlwaysInline
    int methodWhichAccessInstanceField() {
      return x;
    }

    @AlwaysInline
    int methodWhichAccessStaticField() {
      return Result.y;
    }

    @NeverInline
    private int privateMethod() {
      return 0;
    }
  }
}
