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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleLineInfoInlineRemoveTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SingleLineInfoInlineRemoveTest(TestParameters parameters) {
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
  public void testDefaultSourceFile() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
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
                  mainSubject.uniqueMethodWithOriginalName("shouldRemoveLineNumberForInline"),
                  notIf(hasLineNumberTable(), parameters.isDexRuntime()));
            });
  }

  @Test
  public void testManuallySetDefaultSourceFile() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .addKeepRules("-renamesourcefileattribute SourceFile")
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
                  mainSubject.uniqueMethodWithOriginalName("shouldRemoveLineNumberForInline"),
                  notIf(hasLineNumberTable(), parameters.isDexRuntime()));
            });
  }

  @Test
  public void testManuallySetEmptySourceFile() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .addKeepRules("-renamesourcefileattribute")
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
                  mainSubject.uniqueMethodWithOriginalName("shouldRemoveLineNumberForInline"),
                  notIf(hasLineNumberTable(), parameters.isDexRuntime()));
            });
  }

  @Test
  public void testNonDefaultSourceFile() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .addKeepRules("-renamesourcefileattribute SomeBuildTaggedSourceFile")
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
                  mainSubject.uniqueMethodWithOriginalName("shouldRemoveLineNumberForInline"),
                  // TODO(b/146565491): Update to allow dropping the table once supported by ART.
                  hasLineNumberTable());
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

    @NeverInline
    public static void shouldRemoveLineNumberForInline() {
      inlinee();
    }

    public static void main(String[] args) {
      if (args.length == 0) {
        shouldRemoveLineNumberForInline();
      }
    }
  }
}
