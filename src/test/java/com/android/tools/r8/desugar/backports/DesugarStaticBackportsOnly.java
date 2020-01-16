// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.DexInstructionSubject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugarStaticBackportsOnly extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DesugarStaticBackportsOnly(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void checkLongHashCodeDesugared(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertEquals(
        parameters.getApiLevel().isLessThan(AndroidApiLevel.N),
        classSubject
            .uniqueMethodWithName("main")
            .streamInstructions()
            .anyMatch(
                instructionSubject ->
                    instructionSubject.isInvokeStatic()
                        && instructionSubject
                            .toString()
                            .contains("$r8$backportedMethods$utility$Long$1$hashCode")));
    assertEquals(
        parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N),
        classSubject
            .uniqueMethodWithName("main")
            .streamInstructions()
            .anyMatch(
                instructionSubject ->
                    instructionSubject.isInvokeStatic()
                        && instructionSubject.toString().contains("java/lang/Long")));
  }

  @Test
  public void testBackportDesugared() throws Exception {
    String expectedOutput = StringUtils.lines("1234");
    testForD8()
        .addProgramClasses(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(
            options -> options.desugarState = DesugarState.ONLY_BACKPORT_STATICS)
        .compile()
        .inspect(this::checkLongHashCodeDesugared)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void checkLambdaNotDesugared(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClassLambda.class);
    assertThat(classSubject, isPresent());
    assertTrue(
        classSubject
            .uniqueMethodWithName("main")
            .streamInstructions()
            .anyMatch(
                instructionSubject ->
                    ((DexInstructionSubject) instructionSubject).isInvokeCustom()));
  }

  @Test
  public void testLambdaNotDesugared() throws Exception {
    D8TestBuilder builder =
        testForD8()
            .addProgramClasses(TestClassLambda.class)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(
                options -> options.desugarState = DesugarState.ONLY_BACKPORT_STATICS);
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O)) {
      builder.compile().inspect(this::checkLambdaNotDesugared);
    } else {
      try {
        builder.compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertOnlyErrors();
              diagnostics.assertErrorsCount(1);
              Diagnostic diagnostic = diagnostics.getErrors().get(0);
              assertThat(
                  diagnostic.getDiagnosticMessage(),
                  containsString("Invoke-customs are only supported starting with Android O"));
            });
      } catch (CompilationFailedException e) {
        // Expected compilation failed.
        return;
      }
      fail("Expected test to fail with CompilationFailedException");
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println(Long.hashCode(1234));
    }
  }

  static class TestClassLambda {
    public static void main(String[] args) {
      Arrays.asList(args).forEach(s -> System.out.println(s));
    }
  }
}
