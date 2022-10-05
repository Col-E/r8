// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.utils.codeinspector.Matchers.hasLineNumberTable;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleLineInfoMultipleCallsRemoveTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SingleLineInfoMultipleCallsRemoveTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public StackTrace expectedStackTrace;

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForJvm()
            .addTestClasspath()
            .run(CfRuntime.getSystemRuntime(), Main.class)
            .assertFailureWithErrorThatThrows(NullPointerException.class)
            .map(StackTrace::extractFromJvm);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectStackTrace(
            (stackTrace, inspector) -> {
              assertThat(stackTrace, isSame(expectedStackTrace));
              assertThat(inspector.clazz(Builder.class), isPresent());
              ClassSubject mainSubject = inspector.clazz(Main.class);
              assertThat(mainSubject, isPresent());
              assertThat(
                  mainSubject.uniqueMethodWithOriginalName(
                      "shouldRemoveLineNumberForMultipleInvokes"),
                  notIf(hasLineNumberTable(), parameters.isDexRuntime()));
              assertThat(
                  mainSubject.uniqueMethodWithOriginalName("main"),
                  notIf(hasLineNumberTable(), parameters.isDexRuntime()));
            });
  }

  @NeverClassInline
  public static class Builder {

    StringBuilder sb = new StringBuilder();

    @NeverInline
    public Builder add(String str) {
      sb.append(str);
      return this;
    }

    @NeverInline
    public String build() {
      return sb.toString();
    }
  }

  public static class Main {

    @NeverInline
    public static void printOrThrow(String message) {
      if (System.currentTimeMillis() > 0) {
        throw new NullPointerException(message);
      }
      System.out.println(message);
    }

    @NeverInline
    public static void shouldRemoveLineNumberForMultipleInvokes() {
      printOrThrow(new Builder().add("foo").add("bar").add("baz").build());
    }

    public static void main(String[] args) {
      shouldRemoveLineNumberForMultipleInvokes();
    }
  }
}
