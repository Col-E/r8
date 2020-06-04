// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticOrigin;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticPosition;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B152492625 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public B152492625(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void noCallToWait(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    classSubject.forAllMethods(
        foundMethodSubject ->
            foundMethodSubject
                .instructions(InstructionSubject::isInvokeVirtual)
                .forEach(
                    instructionSubject -> {
                      Assert.assertNotEquals(
                          "wait", instructionSubject.getMethod().name.toString());
                    }));
  }

  private Matcher<Diagnostic> matchAssumeNoSideEffectsWarningMessage() {
    return diagnosticMessage(
        containsString(
            "The -assumenosideeffects rule matches methods on `java.lang.Object` with"
                + " wildcards"));
  }

  private Matcher<Diagnostic> matchWarningMessageForAllProblematicMethods() {
    return diagnosticMessage(
        allOf(
            containsString("void notify()"),
            containsString("void notifyAll()"),
            containsString("void wait()"),
            containsString("void wait(long)"),
            containsString("void wait(long, int)")));
  }

  private Matcher<Diagnostic> matchWarningMessageForWaitMethods() {
    return diagnosticMessage(
        allOf(
            containsString("void wait()"),
            containsString("void wait(long)"),
            containsString("void wait(long, int)")));
  }

  private TextRange textRangeForString(String s) {
    return new TextRange(
        new TextPosition(0, 1, 1), new TextPosition(s.length(), 1, s.length() + 1));
  }

  @Test
  public void testR8AllMatch() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class " + B.class.getTypeName() + " { *; }")
        .setMinApi(parameters.getApiLevel())
        .allowDiagnosticWarningMessages()
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertOnlyWarnings();
              diagnostics.assertWarningsMatch(matchAssumeNoSideEffectsWarningMessage());
              diagnostics.assertWarningsMatch(matchWarningMessageForAllProblematicMethods());
            })
        .inspect(this::noCallToWait)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testR8AllMatchMultipleRules() throws Exception {
    class MyOrigin extends Origin {
      private final String part;

      public MyOrigin(String part) {
        super(Origin.root());
        this.part = part;
      }

      @Override
      public String part() {
        return part;
      }
    }

    Origin starRuleOrigin = new MyOrigin("star rule");
    Origin methodsRuleOrigin = new MyOrigin("methods rule");

    String starRule = "-assumenosideeffects class " + B.class.getTypeName() + " { *; }";
    String methodsRule = "-assumenosideeffects class " + B.class.getTypeName() + " { <methods>; }";

    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .apply(
            b ->
                b.getBuilder().addProguardConfiguration(ImmutableList.of(starRule), starRuleOrigin))
        .apply(
            b ->
                b.getBuilder()
                    .addProguardConfiguration(ImmutableList.of(methodsRule), methodsRuleOrigin))
        .setMinApi(parameters.getApiLevel())
        .allowDiagnosticWarningMessages()
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertOnlyWarnings();
              diagnostics.assertWarningsMatch(
                  ImmutableList.of(
                      allOf(
                          matchAssumeNoSideEffectsWarningMessage(),
                          matchWarningMessageForAllProblematicMethods(),
                          diagnosticOrigin(starRuleOrigin),
                          diagnosticPosition(textRangeForString(starRule))),
                      allOf(
                          matchAssumeNoSideEffectsWarningMessage(),
                          matchWarningMessageForAllProblematicMethods(),
                          diagnosticOrigin(methodsRuleOrigin),
                          diagnosticPosition(textRangeForString(methodsRule)))));
            })
        .inspect(this::noCallToWait)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testR8AllMatchDontWarn() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class " + B.class.getTypeName() + " { *; }")
        .addKeepRules("-dontwarn java.lang.Object")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::noCallToWait)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testR8AllMethodsMatch() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class " + B.class.getTypeName() + " { <methods>; }")
        .setMinApi(parameters.getApiLevel())
        .allowDiagnosticWarningMessages()
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertOnlyWarnings();
              diagnostics.assertWarningsMatch(matchAssumeNoSideEffectsWarningMessage());
              diagnostics.assertWarningsMatch(matchWarningMessageForAllProblematicMethods());
            })
        .inspect(this::noCallToWait)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testR8WaitMethodMatch() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class " + B.class.getTypeName() + " { *** w*(...); }")
        .setMinApi(parameters.getApiLevel())
        .allowDiagnosticWarningMessages()
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertOnlyWarnings();
              diagnostics.assertWarningsMatch(matchAssumeNoSideEffectsWarningMessage());
              diagnostics.assertWarningsMatch(matchWarningMessageForWaitMethods());
            })
        .inspect(this::noCallToWait)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testR8WaitSpecificMethodMatch() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class java.lang.Object { void wait(); }")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::noCallToWait)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testR8AssumeNoSideEffectsNotConditional() throws Exception {
    try {
      testForR8(parameters.getBackend())
          .addProgramClasses(TestClass.class, B.class)
          .addKeepMainRule(TestClass.class)
          .addKeepRules(
              "-if class " + TestClass.class.getTypeName(),
              " -assumenosideeffects class " + B.class.getTypeName() + " { *; }")
          .setMinApi(parameters.getApiLevel())
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                diagnostics.assertOnlyErrors();
                diagnostics.assertErrorsMatch(
                    diagnosticMessage(
                        containsString("Expecting '-keep' option after '-if' option")));
              });
      fail("Expected failed compilation");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testProguardNotRemovingWait() throws Exception {
    Assume.assumeTrue(parameters.isCfRuntime());

    testForProguard()
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class " + B.class.getTypeName() + " { *; }")
        .addKeepRules("-dontwarn " + B152492625.class.getTypeName())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(IllegalMonitorStateException.class);
  }

  @Test
  public void testProguardRemovingWait() throws Exception {
    Assume.assumeTrue(parameters.isCfRuntime());

    testForProguard()
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class java.lang.Object { void wait(); }")
        .addKeepRules("-dontwarn " + B152492625.class.getTypeName())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::noCallToWait)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  static class TestClass {

    public void m() throws Exception {
      System.out.println("Hello, world");
      // test fails if wait is not removed.
      wait();
    }

    public static void main(String[] args) throws Exception {
      new TestClass().m();
    }
  }

  static class B {}
}
