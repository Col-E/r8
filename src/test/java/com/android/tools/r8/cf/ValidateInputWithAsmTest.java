// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ValidateInputWithAsmTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test(expected = CompilationFailedException.class)
  public void testD8() throws Exception {
    testForD8(Backend.DEX)
        .apply(this::configure)
        .compileWithExpectedDiagnostics(this::checkDiagnostics);
  }

  @Test(expected = CompilationFailedException.class)
  public void testR8() throws Exception {
    testForR8(Backend.DEX)
        .apply(this::configure)
        .addKeepMainRule(TestClass.class)
        .compileWithExpectedDiagnostics(this::checkDiagnostics);
  }

  private void configure(TestCompilerBuilder<?, ?, ?, ?, ?> builder) throws Exception {
    builder
        .addProgramClassFileData(getInvalidClass())
        .setMinApi(AndroidApiLevel.B)
        .addOptionsModification(options -> options.testing.verifyInputs = true);
  }

  private void checkDiagnostics(TestDiagnosticMessages diagnostics) {
    diagnostics
        .assertOnlyErrors()
        .assertAllErrorsMatch(
            diagnosticMessage(containsString("INVOKEVIRTUAL can't be used with interfaces")));
  }

  private byte[] getInvalidClass() throws Exception {
    return transformer(TestClass.class)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitMethodInsn(
                  final int opcode,
                  final String owner,
                  final String name,
                  final String descriptor,
                  final boolean isInterface) {
                if (owner.endsWith("PrintStream")) {
                  super.visitMethodInsn(opcode, owner, name, descriptor, true);
                } else {
                  super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
              }
            })
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
