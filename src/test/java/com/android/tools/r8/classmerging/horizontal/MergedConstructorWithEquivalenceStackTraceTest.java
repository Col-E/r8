// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForLineNumbers;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import org.junit.Test;

public class MergedConstructorWithEquivalenceStackTraceTest extends HorizontalClassMergingTestBase {

  public MergedConstructorWithEquivalenceStackTraceTest(TestParameters parameters) {
    super(parameters);
  }

  private final String FILE_NAME =
      ToolHelper.getSourceFileForTestClass(getClass()).getFileName().toString();

  private StackTrace getExpectedStackTrace() {
    return StackTrace.builder()
        .add(
            StackTraceLine.builder()
                .setFileName(FILE_NAME)
                .setClassName(typeName(Parent.class))
                .setMethodName("<init>")
                .build())
        .add(
            StackTraceLine.builder()
                .setFileName(FILE_NAME)
                .setClassName(typeName(A.class))
                .setMethodName("<init>")
                .build())
        .add(
            StackTraceLine.builder()
                .setFileName(FILE_NAME)
                .setClassName(typeName(Main.class))
                .setMethodName("main")
                .build())
        .build();
  }

  // TODO(b/301920457): The constructors should be merged in such a way that the original stack can
  //  be recovered.
  private StackTrace getUnxpectedStackTrace() {
    return StackTrace.builder()
        .add(
            StackTraceLine.builder()
                .setFileName(FILE_NAME)
                .setClassName(typeName(Parent.class))
                .setMethodName("<init>")
                .build())
        .add(
            StackTraceLine.builder()
                .setFileName(FILE_NAME)
                .setClassName(typeName(Main.class))
                .setMethodName("main")
                .build())
        .build();
  }

  private void checkRetracedStackTrace(StackTrace expectedStackTrace, StackTrace stackTrace) {
    assertThat(stackTrace, isSameExceptForLineNumbers(expectedStackTrace));
    for (StackTraceLine line : stackTrace.getStackTraceLines()) {
      assertTrue(line.lineNumber > 0);
    }
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    // Get the expected stack trace by running on the JVM.
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), Main.class)
        .assertFailure()
        .inspectStackTrace(actual -> checkRetracedStackTrace(getExpectedStackTrace(), actual));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), not(isPresent()));
              checkRetracedStackTrace(getUnxpectedStackTrace(), stackTrace);
            });
  }

  @NoVerticalClassMerging
  static class Parent {
    Parent() {
      if (System.currentTimeMillis() >= 0) {
        throw new RuntimeException();
      }
    }
  }

  @NeverClassInline
  static class A extends Parent {
    A() {}
  }

  @NeverClassInline
  static class B extends Parent {
    B() {}
  }

  static class Main {
    public static void main(String[] args) {
      new A();
      new B();
    }
  }
}
