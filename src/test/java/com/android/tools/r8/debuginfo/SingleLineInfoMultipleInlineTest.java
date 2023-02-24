// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.utils.codeinspector.Matchers.hasLineNumberTable;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleLineInfoMultipleInlineTest extends TestBase {

  private static StackTrace expectedStackTrace;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForJvm(getStaticTemp())
            .addTestClasspath()
            .run(CfRuntime.getSystemRuntime(), Main.class)
            .assertFailureWithErrorThatThrows(NullPointerException.class)
            .map(StackTrace::extractFromJvm);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectStackTrace(
            (stackTrace, inspector) -> {
              assertThat(stackTrace, isSame(expectedStackTrace));
              ClassSubject mainSubject = inspector.clazz(Main.class);
              assertThat(mainSubject, isPresent());
              assertThat(mainSubject.uniqueMethodWithOriginalName("inlinee"), not(isPresent()));
              assertThat(
                  mainSubject.uniqueMethodWithOriginalName("shouldNotRemoveLineNumberForInline"),
                  notIf(
                      hasLineNumberTable(),
                      parameters.isDexRuntime()
                          && parameters
                              .getApiLevel()
                              .isGreaterThanOrEqualTo(apiLevelWithPcAsLineNumberSupport())));
            });
  }

  public static class Main {

    @NeverInline
    public static void printOrThrow(String message) {
      if (System.currentTimeMillis() > 0) {
        throw new NullPointerException(message);
      }
      System.out.println(message);
    }

    public static void inlinee() {
      printOrThrow("Hello from inlinee");
    }

    public static void inlinee2() {
      printOrThrow("Hello from inlinee2");
    }

    @NeverInline
    public static void shouldNotRemoveLineNumberForInline() {
      inlinee();
      inlinee2();
    }

    public static void main(String[] args) {
      if (args.length == 0) {
        shouldNotRemoveLineNumberForInline();
      }
    }
  }
}
