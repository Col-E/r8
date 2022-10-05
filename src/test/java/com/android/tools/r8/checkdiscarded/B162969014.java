// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.checkdiscarded;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AssumeNoSideEffects;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B162969014 extends TestBase {

  private final boolean checkLogIsDiscarded;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, checkLogIsDiscarded: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public B162969014(boolean checkLogIsDiscarded, TestParameters parameters) {
    this.checkLogIsDiscarded = checkLogIsDiscarded;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult compileResult;
    try {
      compileResult =
          testForR8(parameters.getBackend())
              .addInnerClasses(B162969014.class)
              .addKeepMainRule(TestClass.class)
              .apply(this::applyCheckDiscardedRule)
              .enableAssumeNoSideEffectsAnnotations()
              .enableInliningAnnotations()
              .setMinApi(parameters.getApiLevel())
              .compileWithExpectedDiagnostics(
                  diagnostics -> {
                    if (checkLogIsDiscarded) {
                      diagnostics.assertErrorsMatch(
                          diagnosticMessage(containsString("Discard checks failed.")));
                    } else {
                      diagnostics.assertNoErrors();
                    }
                  });
    } catch (CompilationFailedException e) {
      assertTrue(checkLogIsDiscarded);
      return;
    }

    compileResult
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("foo");
  }

  private void applyCheckDiscardedRule(R8FullTestBuilder builder) {
    if (checkLogIsDiscarded) {
      builder.addKeepRules(
          "-checkdiscard class " + Log.class.getTypeName() + "{",
          "  public static void foo();",
          "  public static void bar();",
          "}");
    }
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject logClassSubject = inspector.clazz(Log.class);
    assertThat(logClassSubject, isPresent());
    assertThat(logClassSubject.uniqueMethodWithOriginalName("foo"), isPresent());
    assertThat(logClassSubject.uniqueMethodWithOriginalName("bar"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      Log.foo();
      Log.bar();
    }
  }

  static class Log {

    @NeverInline
    public static void foo() {
      System.out.println("foo");
    }

    @AssumeNoSideEffects
    @NeverInline
    public static void bar() {
      System.out.println("bar");
    }
  }
}
