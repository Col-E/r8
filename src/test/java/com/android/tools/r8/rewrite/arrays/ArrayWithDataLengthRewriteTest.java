// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.arrays;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.conversion.passes.ArrayConstructionSimplifier;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArrayWithDataLengthRewriteTest extends TestBase {
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public ArrayWithDataLengthRewriteTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static final String[] expectedOutput = {"3", "2"};

  @Test
  public void d8() throws Exception {
    testForD8()
        .setMinApi(parameters)
        .setMode(CompilationMode.RELEASE)
        .addProgramClasses(Main.class)
        .addOptionsModification(opt -> opt.testing.irModifier = this::transformArray)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(expectedOutput)
        .inspect(i -> inspect(i, true));
  }

  @Test
  public void r8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(Main.class)
        .addOptionsModification(opt -> opt.testing.irModifier = this::transformArray)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(expectedOutput)
        .inspect(i -> inspect(i, false));
  }

  private void transformArray(IRCode irCode, AppView<?> appView) {
    new ArrayConstructionSimplifier(appView).run(irCode.context(), irCode);
    String name = irCode.context().getReference().getName().toString();
    if (name.contains("filledArrayData")) {
      assertTrue(irCode.streamInstructions().anyMatch(Instruction::isNewArrayFilledData));
    } else if (name.contains("filledNewArray")) {
      assertTrue(irCode.streamInstructions().anyMatch(Instruction::isInvokeNewArray));
    }
  }

  private void inspect(CodeInspector inspector, boolean d8) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    assertTrue(mainClass.isPresent());
    MethodSubject filledArrayData = mainClass.uniqueMethodWithOriginalName("filledArrayData");
    assertTrue(filledArrayData.streamInstructions().noneMatch(InstructionSubject::isArrayLength));
    if (!d8) {
      MethodSubject filledNewArray = mainClass.uniqueMethodWithOriginalName("filledNewArray");
      assertTrue(filledNewArray.streamInstructions().noneMatch(InstructionSubject::isArrayLength));
    }
  }

  public static final class Main {
    @NeverInline
    public static void filledArrayData() {
      short[] values = new short[3];
      values[0] = 5;
      values[1] = 6;
      values[2] = 1;
      System.out.println(values.length);
    }

    @NeverInline
    public static void filledNewArray() {
      int[] values = new int[] {7, 8};
      System.out.println(values.length);
    }

    public static void main(String[] args) {
      filledArrayData();
      filledNewArray();
    }
  }
}
