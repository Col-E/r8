// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.neverreturnsnormally;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.ForceInline;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CfInstructionSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.DexInstructionSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NeverReturnsNormallyTest extends TestBase {

  private boolean keepOuterTrivial;
  private TestParameters parameters;

  @Parameters(name = "{1}, keep outerTrivial: {0}")
  public static List<Object[]> data() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withAllRuntimes().build());
  }

  public NeverReturnsNormallyTest(boolean keepOuterTrivial, TestParameters parameters) {
    this.keepOuterTrivial = keepOuterTrivial;
    this.parameters = parameters;
  }

  private void runTest(
      BiConsumer<CodeInspector, CompilationMode> inspection,
      boolean enableClassInliner, CompilationMode mode) throws Exception {
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(ForceInline.class));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class));
    builder.setProgramConsumer(emptyConsumer(parameters.getBackend()));
    builder.addLibraryFiles(runtimeJar(parameters.getBackend()));
    builder.setMode(mode);
    builder.addProguardConfiguration(
        ImmutableList.of(
            "-forceinline class * { @com.android.tools.r8.ForceInline *; }",
            "-keep class " + TestClass.class.getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "  *** test*(...);",
            "}",
            "",
            "-dontobfuscate",
            "-allowaccessmodification"),
        Origin.unknown());
    if (keepOuterTrivial) {
      builder.addProguardConfiguration(
          ImmutableList.of(
              "-keep class " + TestClass.class.getTypeName() + " {",
              "  *** outerTrivial(...);",
              "}"),
          Origin.unknown());
    } else {
      builder.addProguardConfiguration(
          ImmutableList.of(
              "-checkdiscard class " + TestClass.class.getTypeName() + " {",
              "  *** assertRemoved(...);",
              "}"),
          Origin.unknown());
    }
    ToolHelper.allowTestProguardOptions(builder);
    AndroidApp app =
        ToolHelper.runR8(
            builder.build(),
            opts -> {
              opts.enableClassInlining = enableClassInliner;
              opts.testing.dontReportFailingCheckDiscarded = true;
            });
    inspection.accept(new CodeInspector(app), mode);

    if (parameters.isDexRuntime()) {
      // Run on Art to check generated code against verifier.
      runOnArt(app, TestClass.class);
    } else {
      assert parameters.isCfRuntime();
      runOnJava(app, TestClass.class);
    }
  }

  private void validate(CodeInspector inspector, CompilationMode mode) {
    assert parameters.isCfRuntime() || parameters.isDexRuntime();
    ClassSubject clazz = inspector.clazz(TestClass.class);
    assertTrue(clazz.isPresent());

    // All calls to 'assertRemoved' are to be removed.
    int numberOfCallsToAssertRemoved = 0;
    for (FoundMethodSubject method : clazz.allMethods()) {
      numberOfCallsToAssertRemoved +=
          method
              .streamInstructions()
              .filter(InstructionSubject::isInvoke)
              .filter(invoke -> invoke.getMethod().name.toString().equals("assertRemoved"))
              .count();
    }
    assertEquals(keepOuterTrivial ? 2 : 0, numberOfCallsToAssertRemoved);

    // Check the instruction used for testInlinedIntoVoidMethod
    MethodSubject methodThrowToBeInlined =
        clazz.method("int", "throwToBeInlined", ImmutableList.of());
    boolean expectedToBePresent = mode == CompilationMode.DEBUG;
    assertEquals(expectedToBePresent, methodThrowToBeInlined.isPresent());
    if (expectedToBePresent) {
      Iterator<InstructionSubject> instructions = methodThrowToBeInlined.iterateInstructions();
      // Call, followed by throw null.
      InstructionSubject insn = nextInstructionSkippingCfPositionAndLabel(instructions);
      assertTrue(insn != null && insn.isConstString(JumboStringMode.ALLOW));
      insn = nextInstruction(instructions);
      assertTrue(insn.isInvoke());
      assertTrue(((InvokeInstructionSubject) insn)
          .invokedMethod().name.toString().equals("throwNpe"));
      verifyTrailingPattern(instructions);
    }

    // Check the instruction used for testInlinedIntoVoidMethod
    MethodSubject methodTestInlinedIntoVoidMethod =
        clazz.method("void", "testInlinedIntoVoidMethod", ImmutableList.of());
    assertTrue(methodTestInlinedIntoVoidMethod.isPresent());
    Iterator<InstructionSubject> instructions =
        methodTestInlinedIntoVoidMethod.iterateInstructions();
    InstructionSubject insn = nextInstructionSkippingCfPositionAndLabel(instructions);
    if (mode == CompilationMode.DEBUG) {
      // Not inlined call to throwToBeInlined.
      assertTrue(insn.isInvoke());
      assertTrue(((InvokeInstructionSubject) insn)
          .invokedMethod().name.toString().equals("throwToBeInlined"));
    } else {
      // Inlined code from throwToBeInlined.
      assertTrue(insn.isConstString(JumboStringMode.ALLOW));
      insn = nextInstructionSkippingCfPositionAndLabel(instructions);
      assertTrue(insn.isInvoke());
      assertTrue(((InvokeInstructionSubject) insn)
          .invokedMethod().name.toString().equals("throwNpe"));
    }
    verifyTrailingPattern(instructions);

    // Check the instruction used for outerTrivial
    MethodSubject methodOuterTrivial = clazz.method("int", "outerTrivial", ImmutableList.of());
    assertEquals(keepOuterTrivial || mode == CompilationMode.DEBUG, methodOuterTrivial.isPresent());

    if (methodOuterTrivial.isPresent()) {
      instructions = methodOuterTrivial.iterateInstructions();
      // Call, followed by [nop, goto]
      insn = nextInstructionSkippingCfPositionAndLabel(instructions);
      assertTrue(insn.isInvoke());
      assertEquals("innerNotReachable", insn.getMethod().name.toString());
      verifyTrailingPattern(instructions);
    }
  }

  private InstructionSubject nextInstruction(Iterator<InstructionSubject> instructions) {
    assertTrue(instructions.hasNext());
    return instructions.next();
  }

  private InstructionSubject nextInstructionSkippingCfPositionAndLabel(
      Iterator<InstructionSubject> instructions) {
    InstructionSubject insn = null;
    while (instructions.hasNext()) {
      insn = instructions.next();
      if (!(insn instanceof CfInstructionSubject)) {
        break;
      }
      CfInstructionSubject cfInsn = (CfInstructionSubject) insn;
      if (!cfInsn.isLabel() && !cfInsn.isPosition()) {
        break;
      }
    }
    return insn;
  }

  private void verifyTrailingPattern(Iterator<InstructionSubject> instructions) {
    InstructionSubject insn = nextInstruction(instructions);
    if (parameters.isDexRuntime()) {
      assertTrue(
          insn instanceof DexInstructionSubject && ((DexInstructionSubject) insn).isConst4());
    } else {
      assertTrue(insn instanceof CfInstructionSubject);
      assertTrue(((CfInstructionSubject) insn).isStackInstruction(Opcode.Pop));
      assertTrue(instructions.hasNext());
      insn = instructions.next();
      assertTrue(insn instanceof CfInstructionSubject);
      assertTrue(((CfInstructionSubject) insn).isConstNull());
    }
    assertTrue(nextInstruction(instructions).isThrow());
    assertFalse(instructions.hasNext());
  }

  @Test
  public void test() throws Exception {
    runTest(this::validate, true, CompilationMode.DEBUG);
    runTest(this::validate, true, CompilationMode.RELEASE);
    runTest(this::validate, false, CompilationMode.DEBUG);
    runTest(this::validate, false, CompilationMode.RELEASE);
  }
}
