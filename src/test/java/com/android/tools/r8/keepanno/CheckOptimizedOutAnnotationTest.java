// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.CheckDiscardDiagnostic;
import com.android.tools.r8.keepanno.annotations.CheckOptimizedOut;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CheckOptimizedOutAnnotationTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A", "B.baz");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public CheckOptimizedOutAnnotationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getInputClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithRuleExtraction() throws Exception {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addProgramClasses(getInputClasses())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .allowDiagnosticWarningMessages()
        .setDiagnosticsLevelModifier(
            (level, diagnostic) ->
                level == DiagnosticsLevel.ERROR ? DiagnosticsLevel.WARNING : level)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics
                  .assertOnlyWarnings()
                  .assertWarningsMatch(
                      DiagnosticsMatcher.diagnosticType(CheckDiscardDiagnostic.class));
              CheckDiscardDiagnostic discard =
                  (CheckDiscardDiagnostic) diagnostics.getWarnings().get(0);
              // The discard error should report one error for A.toString.
              assertEquals(discard.getDiagnosticMessage(), 1, discard.getNumberOfFailures());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkOutput);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, A.class, B.class);
  }

  private void checkOutput(CodeInspector inspector) {
    // A is escaping in the call println(this) and remains in the residual.
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(A.class).uniqueMethodWithOriginalName("toString"), isPresent());

    // Both foo and bar are respectively inlined and dead.
    assertThat(inspector.clazz(A.class).uniqueMethodWithOriginalName("foo"), isAbsent());
    assertThat(inspector.clazz(A.class).uniqueMethodWithOriginalName("bar"), isAbsent());

    // B is fully inlined and not in the residual program.
    assertThat(inspector.clazz(B.class), isAbsent());
  }

  static class A {

    @CheckOptimizedOut
    public void foo() {
      System.out.println(this);
    }

    @CheckOptimizedOut
    public void bar() {
      System.out.println("A.bar");
    }

    @CheckOptimizedOut
    @Override
    public String toString() {
      return "A";
    }
  }

  @CheckOptimizedOut
  static class B {

    public void baz() {
      System.out.println("B.baz");
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo();
      new B().baz();
    }
  }
}
