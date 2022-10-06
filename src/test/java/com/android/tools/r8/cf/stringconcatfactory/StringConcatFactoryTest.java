// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.stringconcatfactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestState;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.examples.JavaExampleClassProxy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringConcatFactoryTest extends TestBase {

  private static final String PKG = "stringconcatfactory";
  private static final String EXAMPLE = "examplesJava9/" + PKG;
  private final JavaExampleClassProxy MAIN =
      new JavaExampleClassProxy(EXAMPLE, PKG + "/StringConcatFactoryUsage");

  static final String EXPECTED = StringUtils.lines("Hello 0");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public StringConcatFactoryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void checkInvokeDynamic(CodeInspector inspector) {
    assertEquals(
        parameters.isCfRuntime(),
        inspector
            .clazz(MAIN.typeName())
            .uniqueMethodWithOriginalName("main")
            .streamInstructions()
            .anyMatch(InstructionSubject::isInvokeDynamic));
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(MAIN.bytes())
        .run(parameters.getRuntime(), MAIN.typeName())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkInvokeDynamic);
  }

  @Test
  public void testJdk8NoIgnoreR8() throws Exception {
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .setMinApi(parameters.getApiLevel())
            .addProgramClassFileData(MAIN.bytes())
            // Always link to the JDK8 rt.jar which has no definition of StringConcatFactory.
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .addKeepMainRule(MAIN.typeName());
    R8TestCompileResult compileResult;
    try {
      compileResult =
          builder.compileWithExpectedDiagnostics(
              diagnostics -> {
                if (parameters.isDexRuntime()) {
                  diagnostics.assertNoMessages();
                } else {
                  diagnostics.assertErrorsMatch(
                      DiagnosticsMatcher.diagnosticType(MissingDefinitionsDiagnostic.class));
                  diagnostics.assertOnlyErrors();
                }
              });
    } catch (CompilationFailedException e) {
      assertTrue(parameters.isCfRuntime());
      return;
    }
    assertTrue(parameters.isDexRuntime());
    compileResult
        .run(parameters.getRuntime(), MAIN.typeName())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkInvokeDynamic);
  }

  @Test
  public void testJdk8WithIgnoreR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramClassFileData(MAIN.bytes())
        // Always link to the JDK8 rt.jar which has no definition of StringConcatFactory.
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .addKeepMainRule(MAIN.typeName())
        .applyIf(
            parameters.isCfRuntime(), b -> b.addDontWarn("java.lang.invoke.StringConcatFactory"))
        .compile()
        .run(parameters.getRuntime(), MAIN.typeName())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkInvokeDynamic);
  }

  @Test
  public void testJdk8WithMapDiagnostic() throws Exception {
    TestDiagnosticMessagesImpl handler =
        new TestDiagnosticMessagesImpl() {
          @Override
          public DiagnosticsLevel modifyDiagnosticsLevel(
              DiagnosticsLevel level, Diagnostic diagnostic) {
            if (diagnostic instanceof MissingDefinitionsDiagnostic) {
              return DiagnosticsLevel.WARNING;
            }
            return super.modifyDiagnosticsLevel(level, diagnostic);
          }
        };
    R8FullTestBuilder.create(new TestState(temp, handler), parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramClassFileData(MAIN.bytes())
        // Always link to the JDK8 rt.jar which has no definition of StringConcatFactory.
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .addKeepMainRule(MAIN.typeName())
        .allowDiagnosticWarningMessages(parameters.isCfRuntime())
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              if (parameters.isDexRuntime()) {
                diagnostics.assertNoMessages();
              } else {
                diagnostics
                    .assertWarningsMatch(
                        DiagnosticsMatcher.diagnosticType(MissingDefinitionsDiagnostic.class))
                    .assertOnlyWarnings();
              }
            })
        .run(parameters.getRuntime(), MAIN.typeName())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkInvokeDynamic);
  }

  @Test
  public void testSystemJdkNoIgnoreClassesR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramClassFileData(MAIN.bytes())
        // The system runtime has StringConcatFactory so link to its bootclasspath.
        .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
        .addKeepMainRule(MAIN.typeName())
        .run(parameters.getRuntime(), MAIN.typeName())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkInvokeDynamic);
  }
}
