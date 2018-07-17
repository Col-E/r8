// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.movestringconstants;

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
import com.android.tools.r8.utils.dexinspector.InstructionSubject;
import com.android.tools.r8.utils.dexinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.dexinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.Test;

public class MoveStringConstantsTest extends TestBase {
  private void runTest(Consumer<DexInspector> inspection) throws Exception {
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(Utils.class));
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMode(CompilationMode.RELEASE);
    builder.addProguardConfiguration(
        ImmutableList.of(
            "-keep class " + TestClass.class.getCanonicalName() + "{ *; }",
            "-dontobfuscate",
            "-allowaccessmodification"
        ),
        Origin.unknown());
    AndroidApp app = ToolHelper.runR8(builder.build());
    inspection.accept(new DexInspector(app));

    // Run on Art to check generated code against verifier.
    runOnArt(app, TestClass.class);
  }

  private void validate(DexInspector inspector) {
    ClassSubject clazz = inspector.clazz(TestClass.class);
    assertTrue(clazz.isPresent());

    // Check the instruction used for testInlinedIntoVoidMethod
    MethodSubject methodThrowToBeInlined =
        clazz.method("void", "foo", ImmutableList.of(
            "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String"));
    assertTrue(methodThrowToBeInlined.isPresent());
    validateSequence(
        methodThrowToBeInlined.iterateInstructions(),
        // 'if' with "foo#1" is flipped.
        InstructionSubject::isIfEqz,

        // 'if' with "foo#2" is removed along with the constant.

        // 'if' with "foo#3" is removed so now we have unconditional call.
        insn -> insn.isConstString("StringConstants::foo#3", JumboStringMode.DISALLOW),
        InstructionSubject::isInvokeStatic,
        InstructionSubject::isThrow,

        // 'if's with "foo#4" and "foo#5" are flipped, but their throwing branches
        // are not moved to the end of the code (area for improvement?).
        insn -> insn.isConstString("StringConstants::foo#4", JumboStringMode.DISALLOW),
        InstructionSubject::isIfEqz, // Flipped if
        InstructionSubject::isGoto, // Jump around throwing branch.
        InstructionSubject::isInvokeStatic, // Throwing branch.
        InstructionSubject::isThrow,
        insn -> insn.isConstString("StringConstants::foo#5", JumboStringMode.DISALLOW),
        InstructionSubject::isIfEqz, // Flipped if
        InstructionSubject::isReturnVoid, // Final return statement.
        InstructionSubject::isInvokeStatic, // Throwing branch.
        InstructionSubject::isThrow,

        // After 'if' with "foo#1" flipped, always throwing branch
        // moved here along with the constant.
        insn -> insn.isConstString("StringConstants::foo#1", JumboStringMode.DISALLOW),
        InstructionSubject::isInvokeStatic,
        InstructionSubject::isThrow);
  }

  @SafeVarargs
  private final void validateSequence(
      Iterator<InstructionSubject> instructions, Predicate<InstructionSubject>... checks) {
    int index = 0;

    while (instructions.hasNext()) {
      if (index >= checks.length) {
        return;
      }
      if (checks[index].test(instructions.next())) {
        index++;
      }
    }

    assertTrue("Not all checks processed", index >= checks.length);
  }

  @Test
  public void test() throws Exception {
    runTest(this::validate);
  }
}
