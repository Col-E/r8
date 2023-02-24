// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.utils.codeinspector.Matchers.hasLineNumberTable;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleLineInfoRemoveTest extends TestBase {

  private static StackTrace expectedStackTrace;

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean customSourceFile;

  @Parameters(name = "{0}, custom-source-file:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
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
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .applyIf(
            customSourceFile,
            b -> b.getBuilder().setSourceFileProvider(env -> "MyCustomSourceFile"))
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectOriginalStackTrace(
            stackTrace -> {
              for (StackTraceLine line : stackTrace.getStackTraceLines()) {
                if (customSourceFile) {
                  assertEquals("MyCustomSourceFile", line.fileName);
                } else if (parameters.isCfRuntime()) {
                  assertEquals("SourceFile", line.fileName);
                } else {
                  assertThat(
                      line.fileName,
                      CoreMatchers.anyOf(
                          CoreMatchers.is("SourceFile"), CoreMatchers.is("Unknown Source")));
                }
              }
            })
        .inspectStackTrace(
            (stackTrace, inspector) -> {
              assertThat(stackTrace, isSame(expectedStackTrace));
              ClassSubject mainSubject = inspector.clazz(Main.class);
              assertThat(mainSubject, isPresent());
              assertThat(
                  mainSubject.uniqueMethodWithOriginalName("shouldRemoveLineNumber"),
                  notIf(hasLineNumberTable(), canSingleLineDebugInfoBeDiscarded()));
            });
  }

  private boolean canSingleLineDebugInfoBeDiscarded() {
    return parameters.isDexRuntime()
        && !customSourceFile
        && parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithPcAsLineNumberSupport());
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
    public static void shouldRemoveLineNumber() {
      printOrThrow("Hello World");
    }

    public static void main(String[] args) {
      shouldRemoveLineNumber();
    }
  }
}
