// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.arrays;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArrayWithDataLengthRewriteTest extends TestBase {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return buildParameters(getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  private final TestParameters parameters;

  public ArrayWithDataLengthRewriteTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static final String[] expectedOutput = {"3"};

  @Test
  public void d8() throws Exception {
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .setMode(CompilationMode.RELEASE)
        .addProgramClasses(Main.class)
        .addOptionsModification(opt -> opt.testing.irModifier = this::transformArray)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(expectedOutput)
        .inspect(this::assertNoArrayLength);
  }

  @Test
  public void r8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getRuntime())
        .addProgramClasses(Main.class)
        .addOptionsModification(opt -> opt.testing.irModifier = this::transformArray)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(expectedOutput)
        .inspect(this::assertNoArrayLength);
  }

  private void transformArray(IRCode irCode, AppView<?> appView) {
    if (irCode.context().getReference().getName().toString().contains("main")) {
      new CodeRewriter(appView).simplifyArrayConstruction(irCode);
      assertTrue(irCode.streamInstructions().anyMatch(Instruction::isNewArrayFilledData));
    }
  }

  private void assertNoArrayLength(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    assertTrue(mainClass.isPresent());
    assertTrue(
        mainClass.mainMethod().streamInstructions().noneMatch(InstructionSubject::isArrayLength));
  }

  public static final class Main {
    public static void main(String[] args) {
      int[] ints = new int[3];
      ints[0] = 5;
      ints[1] = 6;
      ints[2] = 1;
      System.out.println(ints.length);
    }
  }
}
