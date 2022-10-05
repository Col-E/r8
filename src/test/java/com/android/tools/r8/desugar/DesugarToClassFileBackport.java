// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfArithmeticBinop.Opcode;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.dex.code.DexAddLong2Addr;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugarToClassFileBackport extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private final TestParameters parameters;

  public DesugarToClassFileBackport(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean isCfLAdd(CfInstruction instruction) {
    return (instruction instanceof CfArithmeticBinop)
        && (((CfArithmeticBinop) instruction).getOpcode() == Opcode.Add)
        && (((CfArithmeticBinop) instruction).getType() == NumericType.LONG);
  }

  private boolean isDexAddLong(DexInstruction instruction) {
    return instruction instanceof DexAddLong2Addr;
  }

  private boolean boxedDoubleIsFiniteInvoke(InstructionSubject instruction) {
    System.out.println(instruction);
    if (instruction.isInvokeStatic()) {
      System.out.println(instruction.getMethod().qualifiedName());
    }
    return instruction.isInvokeStatic()
        && instruction.getMethod().qualifiedName().equals("java.lang.Double.isFinite");
  }

  private void checkBackportingNotRequired(CodeInspector inspector) {
    MethodSubject methodSubject =
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("main");
    if (methodSubject.getProgramMethod().getDefinition().getCode().isCfCode()) {
      CfCode code = methodSubject.getProgramMethod().getDefinition().getCode().asCfCode();
      assertTrue(code.getInstructions().stream().noneMatch(this::isCfLAdd));
    } else {
      DexCode code = methodSubject.getProgramMethod().getDefinition().getCode().asDexCode();
      assertTrue(Arrays.stream(code.instructions).noneMatch(this::isDexAddLong));
    }
    assertTrue(methodSubject.streamInstructions().anyMatch(this::boxedDoubleIsFiniteInvoke));
  }

  private void checkBackportingRequired(CodeInspector inspector) {
    MethodSubject methodSubject =
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("main");
    if (methodSubject.getProgramMethod().getDefinition().getCode().isCfCode()) {
      CfCode code = methodSubject.getProgramMethod().getDefinition().getCode().asCfCode();
      assertTrue(code.getInstructions().stream().anyMatch(this::isCfLAdd));
    } else {
      DexCode code = methodSubject.getProgramMethod().getDefinition().getCode().asDexCode();
      assertTrue(Arrays.stream(code.instructions).anyMatch(this::isDexAddLong));
    }
    assertTrue(methodSubject.streamInstructions().noneMatch(this::boxedDoubleIsFiniteInvoke));
  }

  private void checkBackportedIfRequired(CodeInspector inspector) {
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)) {
      checkBackportingNotRequired(inspector);
    } else {
      checkBackportingRequired(inspector);
    }
  }

  @Test
  public void test() throws Exception {
    testForDesugaring(parameters)
        .addInnerClasses(DesugarToClassFileBackport.class)
        .run(parameters.getRuntime(), TestClass.class)
        .inspectIf(DesugarTestConfiguration::isNotDesugared, this::checkBackportingNotRequired)
        .inspectIf(DesugarTestConfiguration::isDesugared, this::checkBackportedIfRequired)
        .assertSuccessWithOutputLines("3", "true");
  }

  static class TestClass {

    private static long getOne() {
      return 1L;
    }

    private static long getTwo() {
      return 2L;
    }

    public static void main(String[] args) {
      System.out.println(Long.sum(getOne(), getTwo()));
      System.out.println(Double.isFinite(1.0));
    }
  }
}
