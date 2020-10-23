// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import org.junit.Before;
import org.junit.Test;

public class MergedVirtualMethodStackTraceTest extends HorizontalClassMergingTestBase {
  public MergedVirtualMethodStackTraceTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
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
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addHorizontallyMergedClassesInspectorIf(
            enableHorizontalClassMerging,
            inspector -> inspector.assertMergedInto(Program.B.class, Program.A.class))
        .run(parameters.getRuntime(), Program.Main.class)
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              assertThat(codeInspector.clazz(Program.A.class), isPresent());
              assertThat(
                  codeInspector.clazz(Program.B.class),
                  notIf(isPresent(), enableHorizontalClassMerging));
              if (enableHorizontalClassMerging) {
                StackTrace expectedStackTraceWithMergedMethod =
                    StackTrace.builder()
                        .add(expectedStackTrace)

                        .add(
                            1,
                            StackTraceLine.builder()
                                .setClassName(Program.A.class.getTypeName())
                                .setMethodName("foo$bridge")
                                .setFileName("Program.java")
                                .setFileName(getClass().getSimpleName() + ".java")
                                .setLineNumber(stackTrace.get(1).lineNumber)
                                .build())
                        .build();
                assertThat(stackTrace, isSame(expectedStackTraceWithMergedMethod));
              }
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
