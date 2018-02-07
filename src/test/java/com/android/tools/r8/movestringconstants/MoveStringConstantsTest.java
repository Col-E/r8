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
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.InstructionSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
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
    builder.setMinApiLevel(AndroidApiLevel.O.getLevel());
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
        clazz.method("void", "foo", ImmutableList.of("java.lang.String"));
    assertTrue(methodThrowToBeInlined.isPresent());
    validateSequence(methodThrowToBeInlined.iterateInstructions(),
        InstructionSubject::isIfNez,
        insn -> insn.isConstString("StringConstants::foo#1"),
        // Below two are removed by optimization: non-null argument "".
        // InstructionSubject::isIfNez,
        // insn -> insn.isConstString("StringConstants::foo#2"),
        // InstructionSubject::isIfNez, 'removed by optimization'
        insn -> insn.isConstString("StringConstants::foo#3")
        // Below four are removed, since a safe call of arg.length() indicates arg is not null.
        // insn -> insn.isConstString("StringConstants::foo#4"),
        // InstructionSubject::isIfNez,
        // insn -> insn.isConstString("StringConstants::foo#5"),
        // InstructionSubject::isIfNez
    );
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
