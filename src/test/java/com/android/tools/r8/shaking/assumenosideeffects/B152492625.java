// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticOrigin;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticPosition;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static java.util.Collections.emptyList;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B152492625 extends TestBase {

  private final TestParameters parameters;
  private final boolean dontWarnObject;

  @Parameterized.Parameters(name = "{0}, dontWarnObject {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public B152492625(TestParameters parameters, boolean dontWarnObject) {
    this.parameters = parameters;
    this.dontWarnObject = dontWarnObject;
  }

  private void checkIfWaitIsInvokedFromMain(CodeInspector inspector, boolean isR8) {
    MethodSubject mainMethodSubject = inspector.clazz(TestClass.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertThat(
        mainMethodSubject,
        onlyIf(isR8, invokesMethod("void", "java.lang.Object", "wait", emptyList())));
  }

  private Matcher<Diagnostic> matchAssumeNoSideEffectsMessage() {
    return diagnosticMessage(
        containsString(
            "The -assumenosideeffects rule matches the following method(s) on java.lang.Object: "));
  }

  private Matcher<Diagnostic> matchMessageForAllProblematicMethods() {
    return diagnosticMessage(
        allOf(
            containsString("notify()"),
            containsString("notifyAll()"),
            containsString("wait()"),
            containsString("wait(long)"),
            containsString("wait(long, int)")));
  }

  private Matcher<Diagnostic> matchMessageForWaitMethods() {
    return diagnosticMessage(
        allOf(
            containsString("wait()"),
            containsString("wait(long)"),
            containsString("wait(long, int)")));
  }

  private void assertErrorsOrWarnings(
      TestDiagnosticMessages diagnostics, List<Matcher<Diagnostic>> matchers) {
    if (dontWarnObject) {
      diagnostics.assertNoMessages();
    } else {
      diagnostics.assertOnlyWarnings();
      diagnostics.assertWarningsMatch(matchers);
    }
  }

  private TextRange textRangeForString(String s) {
    return new TextRange(
        new TextPosition(0, 1, 1), new TextPosition(s.length(), 1, s.length() + 1));
  }

  @Test
  public void testR8AllMatch() throws Exception {
    List<Matcher<Diagnostic>> matchers =
        ImmutableList.of(
            allOf(matchAssumeNoSideEffectsMessage(), matchMessageForAllProblematicMethods()));

    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .applyIf(dontWarnObject, tb -> tb.addKeepRules("-dontwarn java.lang.Object"))
        .addKeepRules("-assumenosideeffects class " + B.class.getTypeName() + " { *; }")
        .setMinApi(parameters.getApiLevel())
        .allowDiagnosticWarningMessages(!dontWarnObject)
        .compileWithExpectedDiagnostics(
            diagnostics -> assertErrorsOrWarnings(diagnostics, matchers))
        .inspect(inspector -> checkIfWaitIsInvokedFromMain(inspector, true))
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

    List<Matcher<Diagnostic>> matchers =
        ImmutableList.of(
            allOf(
                matchAssumeNoSideEffectsMessage(),
                matchMessageForAllProblematicMethods(),
                diagnosticOrigin(starRuleOrigin),
                diagnosticPosition(textRangeForString(starRule))),
            allOf(
                matchAssumeNoSideEffectsMessage(),
                matchMessageForAllProblematicMethods(),
                diagnosticOrigin(methodsRuleOrigin),
                diagnosticPosition(textRangeForString(methodsRule))));

    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .applyIf(dontWarnObject, tb -> tb.addKeepRules("-dontwarn java.lang.Object"))
        .apply(
            b ->
                b.getBuilder().addProguardConfiguration(ImmutableList.of(starRule), starRuleOrigin))
        .apply(
            b ->
                b.getBuilder()
                    .addProguardConfiguration(ImmutableList.of(methodsRule), methodsRuleOrigin))
        .setMinApi(parameters.getApiLevel())
        .allowDiagnosticWarningMessages(!dontWarnObject)
        .compileWithExpectedDiagnostics(
            diagnostics -> assertErrorsOrWarnings(diagnostics, matchers))
        .inspect(inspector -> checkIfWaitIsInvokedFromMain(inspector, true))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testR8NonProblemeticMatchDontWarn() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .applyIf(dontWarnObject, tb -> tb.addKeepRules("-dontwarn java.lang.Object"))
        .addKeepRules("-assumenosideeffects class " + B.class.getTypeName() + " { hash*(); }")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testR8AllMethodsMatch() throws Exception {
    List<Matcher<Diagnostic>> matchers =
        ImmutableList.of(
            allOf(matchAssumeNoSideEffectsMessage(), matchMessageForAllProblematicMethods()));

    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .applyIf(dontWarnObject, tb -> tb.addKeepRules("-dontwarn java.lang.Object"))
        .addKeepRules("-assumenosideeffects class " + B.class.getTypeName() + " { <methods>; }")
        .setMinApi(parameters.getApiLevel())
        .allowDiagnosticWarningMessages(!dontWarnObject)
        .compileWithExpectedDiagnostics(
            diagnostics -> assertErrorsOrWarnings(diagnostics, matchers))
        .inspect(inspector -> checkIfWaitIsInvokedFromMain(inspector, true))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testR8WaitMethodMatch() throws Exception {
    List<Matcher<Diagnostic>> matchers =
        ImmutableList.of(allOf(matchAssumeNoSideEffectsMessage(), matchMessageForWaitMethods()));

    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .applyIf(dontWarnObject, tb -> tb.addKeepRules("-dontwarn java.lang.Object"))
        .addKeepRules("-assumenosideeffects class " + B.class.getTypeName() + " { *** w*(...); }")
        .setMinApi(parameters.getApiLevel())
        .allowDiagnosticWarningMessages(!dontWarnObject)
        .compileWithExpectedDiagnostics(
            diagnostics -> assertErrorsOrWarnings(diagnostics, matchers))
        .inspect(inspector -> checkIfWaitIsInvokedFromMain(inspector, true))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testR8WaitSpecificMethodMatch() throws Exception {
    assumeTrue("No need to run this with -dontwarn java.lang.Object", !dontWarnObject);

    List<Matcher<Diagnostic>> matchers = ImmutableList.of(matchAssumeNoSideEffectsMessage());

    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class java.lang.Object { void wait(); }")
        .setMinApi(parameters.getApiLevel())
        .allowDiagnosticWarningMessages()
        .compileWithExpectedDiagnostics(
            diagnostics -> assertErrorsOrWarnings(diagnostics, matchers))
        .inspect(inspector -> checkIfWaitIsInvokedFromMain(inspector, true))
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
    assumeTrue("No need to run this with -dontwarn java.lang.Object", !dontWarnObject);
    assumeTrue(parameters.isCfRuntime());

    testForProguard(ProguardVersion.getLatest())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class " + B.class.getTypeName() + " { *; }")
        .addKeepRules("-dontwarn " + B152492625.class.getTypeName())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testProguardRemovingWait() throws Exception {
    assumeTrue("No need to run this with -dontwarn java.lang.Object", !dontWarnObject);
    assumeTrue(parameters.isCfRuntime());

    testForProguard(ProguardVersion.getLatest())
        .addProgramClasses(TestClass.class, B.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class java.lang.Object { void wait(); }")
        .addKeepRules("-dontwarn " + B152492625.class.getTypeName())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(inspector -> checkIfWaitIsInvokedFromMain(inspector, false))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("Hello");
  }

  static class TestClass {

    public void m() throws Exception {
      System.out.print("Hello");
      // test fails if wait is removed.
      try {
        wait();
      } catch (IllegalMonitorStateException e) {
        System.out.println(", world");
      }
    }

    public static void main(String[] args) throws Exception {
      new TestClass().m();
    }
  }

  static class B {}
}
