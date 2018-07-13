// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.neverreturnsnormally;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.android.tools.r8.utils.dexinspector.DexInstructionSubject;
import com.android.tools.r8.utils.dexinspector.InstructionSubject;
import com.android.tools.r8.utils.dexinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.dexinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.function.BiConsumer;
import org.junit.Test;

public class NeverReturnsNormallyTest extends TestBase {
  private void runTest(
      BiConsumer<DexInspector, CompilationMode> inspection,
      boolean enableClassInliner, CompilationMode mode) throws Exception {
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class));
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMode(mode);
    builder.addProguardConfiguration(
        ImmutableList.of(
            "-keep class " + TestClass.class.getCanonicalName() + "{",
            "  public static void main(java.lang.String[]);",
            "  *** test*(...);",
            "  *** throwToBeInlined(...);",
            "  *** outerTrivial(...);",
            "}",
            "",
            "-checkdiscard class " + TestClass.class.getCanonicalName() + "{",
            "  *** assertRemoved(...);",
            "}",
            "",
            "-dontobfuscate",
            "-allowaccessmodification"
        ),
        Origin.unknown());
    AndroidApp app = ToolHelper.runR8(builder.build(),
        opts -> opts.enableClassInlining = enableClassInliner);
    inspection.accept(new DexInspector(app), mode);

    // Run on Art to check generated code against verifier.
    runOnArt(app, TestClass.class);
  }

  private void validate(DexInspector inspector, CompilationMode mode) {
    ClassSubject clazz = inspector.clazz(TestClass.class);
    assertTrue(clazz.isPresent());

    // All calls to 'assertRemoved' are to be removed.
    clazz.forAllMethods(method -> {
      Iterator<InstructionSubject> instructions =
          method.iterateInstructions(InstructionSubject::isInvoke);
      while (instructions.hasNext()) {
        InvokeInstructionSubject invoke = (InvokeInstructionSubject) instructions.next();
        assertFalse(invoke.invokedMethod().name.toString().equals("assertRemoved"));
      }
    });

    // Check the instruction used for testInlinedIntoVoidMethod
    MethodSubject methodThrowToBeInlined =
        clazz.method("int", "throwToBeInlined", ImmutableList.of());
    assertTrue(methodThrowToBeInlined.isPresent());
    Iterator<InstructionSubject> instructions = methodThrowToBeInlined.iterateInstructions();
    // Call, followed by throw null.
    assertTrue(nextInstruction(instructions).isConstString());
    InstructionSubject insn = nextInstruction(instructions);
    assertTrue(insn.isInvoke());
    assertTrue(((InvokeInstructionSubject) insn)
        .invokedMethod().name.toString().equals("throwNpe"));
    insn = nextInstruction(instructions);
    assertTrue(insn instanceof DexInstructionSubject && ((DexInstructionSubject) insn).isConst4());
    assertTrue(nextInstruction(instructions).isThrow());
    assertFalse(instructions.hasNext());

    // Check the instruction used for testInlinedIntoVoidMethod
    MethodSubject methodTestInlinedIntoVoidMethod =
        clazz.method("void", "testInlinedIntoVoidMethod", ImmutableList.of());
    assertTrue(methodTestInlinedIntoVoidMethod.isPresent());
    instructions = methodTestInlinedIntoVoidMethod.iterateInstructions();
    if (mode == CompilationMode.DEBUG) {
      // Not inlined call to throwToBeInlined.
      insn = nextInstruction(instructions);
      assertTrue(insn.isInvoke());
      assertTrue(((InvokeInstructionSubject) insn)
          .invokedMethod().name.toString().equals("throwToBeInlined"));
    } else {
      // Inlined code from throwToBeInlined.
      assertTrue(nextInstruction(instructions).isConstString());
      insn = nextInstruction(instructions);
      assertTrue(insn.isInvoke());
      assertTrue(((InvokeInstructionSubject) insn)
          .invokedMethod().name.toString().equals("throwNpe"));
    }
    insn = nextInstruction(instructions);
    assertTrue(insn instanceof DexInstructionSubject && ((DexInstructionSubject) insn).isConst4());
    assertTrue(nextInstruction(instructions).isThrow());
    assertFalse(instructions.hasNext());

    // Check the instruction used for testInlinedIntoVoidMethod
    MethodSubject methodOuterTrivial =
        clazz.method("int", "outerTrivial", ImmutableList.of());
    assertTrue(methodOuterTrivial.isPresent());
    instructions = methodOuterTrivial.iterateInstructions();
    // Call, followed by [nop, goto]
    insn = nextInstruction(instructions);
    assertTrue(insn.isInvoke());
    assertTrue(((InvokeInstructionSubject) insn)
        .invokedMethod().name.toString().equals("innerNotReachable"));
    insn = nextInstruction(instructions);
    assertTrue(insn instanceof DexInstructionSubject && ((DexInstructionSubject) insn).isConst4());
    assertTrue(nextInstruction(instructions).isThrow());
    assertFalse(instructions.hasNext());
  }

  private InstructionSubject nextInstruction(Iterator<InstructionSubject> instructions) {
    assertTrue(instructions.hasNext());
    return instructions.next();
  }

  @Test
  public void test() throws Exception {
    runTest(this::validate, true, CompilationMode.DEBUG);
    runTest(this::validate, true, CompilationMode.RELEASE);
    runTest(this::validate, false, CompilationMode.DEBUG);
    runTest(this::validate, false, CompilationMode.RELEASE);
  }
}
