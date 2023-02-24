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
import org.junit.BeforeClass;
import org.junit.Test;

public class MergedConstructorStackTraceTest extends HorizontalClassMergingTestBase {

  private static StackTrace expectedStackTrace;

  public MergedConstructorStackTraceTest(TestParameters parameters) {
    super(parameters);
  }

  @BeforeClass
  public static void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForJvm(getStaticTemp())
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
              assertThat(stackTrace, isSame(expectedStackTrace));
              assertThat(codeInspector.clazz(B.class), not(isPresent()));
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
    A() {
      // To avoid constructor equivalence.
      System.out.println("A");
    }
  }

  @NeverClassInline
  static class B extends Parent {
    B() {
      // To avoid constructor equivalence.
      System.out.println("B");
    }
  }

  static class Main {
    public static void main(String[] args) {
      new A();
      new B();
    }
  }
}
