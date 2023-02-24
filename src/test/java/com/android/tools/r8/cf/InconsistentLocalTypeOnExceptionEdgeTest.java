// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InconsistentLocalTypeOnExceptionEdgeTest extends JasminTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    List<byte[]> classFileData = getProgramClassFileData();
    String main = "Main";
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClassFileData(classFileData)
          .run(parameters.getRuntime(), main)
          .assertFailureWithErrorThatThrows(VerifyError.class);
    } else {
      try {
        testForD8().addProgramClassFileData(classFileData).setMinApi(parameters).compile();
      } catch (CompilationFailedException e) {
        inspectCompilationFailedException(e);
      }
    }

    try {
      testForR8(parameters.getBackend())
          .addProgramClassFileData(classFileData)
          .addKeepAllClassesRule()
          .allowDiagnosticWarningMessages()
          .setMinApi(parameters)
          .compileWithExpectedDiagnostics(
              diagnostics ->
                  diagnostics.assertWarningsMatch(
                      allOf(
                          diagnosticType(UnverifiableCfCodeDiagnostic.class),
                          diagnosticMessage(
                              allOf(
                                  containsString(
                                      "Unverifiable code in `void Main.main(java.lang.String[])`"),
                                  containsString(
                                      "Expected object at local index 0, but was top"))))));
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      inspectCompilationFailedException(e);
    }
  }

  private List<byte[]> getProgramClassFileData() throws Exception {
    JasminBuilder appBuilder = new JasminBuilder();
    ClassBuilder classBuilder = appBuilder.addClass("Main");
    classBuilder.addStaticField("FIELD", "I");
    classBuilder.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "LabelTryStart:",
        // At this throwing instruction we have locals=[0: java.lang.String[]].
        "  getstatic Main/FIELD I",
        "  istore 0",
        "  aconst_null",
        // At this throwing instruction we have locals=[0: int].
        "  athrow",
        "LabelTryEnd:",
        "LabelCatch:",
        // Unsafe attempt to read an object at local index 0.
        "  aload 0",
        "  invokestatic java/util/Arrays/toString([Ljava/lang/Object;)Ljava/lang/String;",
        "  pop",
        "  return",
        ".catch java/lang/Throwable from LabelTryStart to LabelTryEnd using LabelCatch");
    return appBuilder.buildClasses();
  }

  private void inspectCompilationFailedException(CompilationFailedException e) {
    assertThat(
        e.getCause().getMessage(),
        containsString("Cannot constrain type: INT for value: v1 by constraint: OBJECT"));
  }
}
