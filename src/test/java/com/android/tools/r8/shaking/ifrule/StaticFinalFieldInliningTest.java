// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.JavaCompilerTool;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.InlinableStaticFinalFieldPreconditionDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StaticFinalFieldInliningTest extends TestBase {

  static final String EXPECTED =
      StringUtils.lines("null", "const", "null", "const", "21", "42", "42.5");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public StaticFinalFieldInliningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJavaCompilers() throws Exception {
    Path output =
        JavaCompilerTool.create(parameters.getRuntime().asCf(), temp)
            .addSourceFiles(
                ToolHelper.getSourceFileForTestClass(StaticFinalFieldInliningSource.class))
            .compile();

    testForJvm(parameters)
        .addProgramFiles(output)
        .run(parameters.getRuntime(), StaticFinalFieldInliningSource.class)
        .assertSuccessWithOutput(EXPECTED);

    CodeInspector inspector = new CodeInspector(output);
    assertHasStaticGet(true, "getNullObject", inspector);
    assertHasStaticGet(true, "getConstObject", inspector);
    assertHasStaticGet(true, "getNullString", inspector);
    assertHasStaticGet(false, "getConstString", inspector);
    assertHasStaticGet(true, "getNonConstInt", inspector);
    assertHasStaticGet(false, "getConstInt", inspector);
    assertHasStaticGet(false, "getConstDouble", inspector);
  }

  private static void assertHasStaticGet(
      boolean expected, String methodName, CodeInspector inspector) {
    MethodSubject method =
        inspector
            .clazz(StaticFinalFieldInliningSource.class)
            .uniqueMethodWithOriginalName(methodName);
    assertEquals(
        method.getMethod().codeToString(),
        expected,
        method.streamInstructions().anyMatch(InstructionSubject::isStaticGet));
  }

  private static String makeIfRule(String fieldName) {
    return "-if class * { *** " + fieldName + "; } -keep class <1> { *** unusedField; }";
  }

  @Test
  public void testNoWarningsOnNonInlinedFields() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(StaticFinalFieldInliningSource.class)
        .addKeepMainRule(StaticFinalFieldInliningSource.class)
        .addKeepRules(makeIfRule("nullObject"))
        .addKeepRules(makeIfRule("constObject"))
        .addKeepRules(makeIfRule("nullString"))
        .addKeepRules(makeIfRule("nonConstInt"))
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoWarnings);
  }

  @Test
  public void testWarningsOnMultipleMatches() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(StaticFinalFieldInliningSource.class)
        .addKeepMainRule(StaticFinalFieldInliningSource.class)
        // This rule matches both constObject (non-inlined) and constString/Int/Double (inlined).
        .addKeepRules(makeIfRule("const*"))
        .allowDiagnosticWarningMessages()
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertWarningsMatch(
                  allOf(
                      diagnosticType(InlinableStaticFinalFieldPreconditionDiagnostic.class),
                      diagnosticMessage(containsString("constString")),
                      diagnosticMessage(containsString("constInt")),
                      diagnosticMessage(containsString("constDouble")),
                      not(diagnosticMessage(containsString("constObject")))));
            });
  }

  @Test
  public void testWarningsOnInlinedFields() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(StaticFinalFieldInliningSource.class)
        .addKeepMainRule(StaticFinalFieldInliningSource.class)
        .addKeepRules(makeIfRule("constString"))
        .addKeepRules(makeIfRule("constInt"))
        .addKeepRules(makeIfRule("constDouble"))
        .allowDiagnosticWarningMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertWarningsMatch(
                    diagnosticMessage(containsString("constString")),
                    diagnosticMessage(containsString("constInt")),
                    diagnosticMessage(containsString("constDouble"))));
  }
}
