// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import org.junit.Before;
import org.junit.Test;

public class MergedConstructorStackTraceTest extends HorizontalClassMergingTestBase {

  public StackTrace expectedStackTrace;

  public MergedConstructorStackTraceTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForJvm()
            .addTestClasspath()
            .run(CfRuntime.getSystemRuntime(), Main.class)
            .assertFailure()
            .map(StackTrace::extractFromJvm);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              if (enableHorizontalClassMerging) {
                StackTrace expectedStackTraceWithMergedConstructor =
                    StackTrace.builder()
                        .add(expectedStackTrace)
                        .add(
                            2,
                            StackTraceLine.builder()
                                .setClassName(A.class.getTypeName())
                                .setMethodName("<init>")
                                .setFileName(getClass().getSimpleName() + ".java")
                                .setLineNumber(0)
                                .build())
                        .build();
                assertThat(stackTrace, isSame(expectedStackTraceWithMergedConstructor));
                assertThat(codeInspector.clazz(B.class), not(isPresent()));
              } else {
                assertThat(stackTrace, isSame(expectedStackTrace));
                assertThat(codeInspector.clazz(B.class), isPresent());
              }
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
