// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.naming.retrace.StackTrace;
import org.junit.Before;
import org.junit.Test;

public class MergedVirtualMethodStackTraceTest extends HorizontalClassMergingTestBase {
  public MergedVirtualMethodStackTraceTest(TestParameters parameters) {
    super(parameters);
  }

  public StackTrace expectedStackTrace;

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForJvm()
            .addTestClasspath()
            .run(CfRuntime.getSystemRuntime(), Program.Main.class)
            .assertFailure()
            .map(StackTrace::extractFromJvm);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(Program.class)
        .addKeepMainRule(Program.Main.class)
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .addDontWarn(C.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(Program.B.class, Program.A.class))
        .run(parameters.getRuntime(), Program.Main.class)
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              assertThat(codeInspector.clazz(Program.A.class), isPresent());
              assertThat(codeInspector.clazz(Program.B.class), isAbsent());
              assertThat(stackTrace, isSame(expectedStackTrace));
            });
  }

  public static class C {
    public static void foo() {
      System.out.println("foo c");
    }
  }

  public static class Program {
    @NeverClassInline
    public static class A {
      @NeverInline
      public void foo() {
        System.out.println("foo a");
        try {
          // Undefined reference, prevents inlining.
          C.foo();
        } catch (NoClassDefFoundError e) {
        }
      }
    }

    @NeverClassInline
    public static class B {
      @NeverInline
      public void foo() {
        try {
          // Undefined reference, prevents inlining.
          C.foo();
        } catch (NoClassDefFoundError e) {
        }
        throw new RuntimeException();
      }
    }

    public static class Main {
      public static void main(String[] args) {
        new A().foo();
        new B().foo();
      }
    }
  }
}
