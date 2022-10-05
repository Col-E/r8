// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;

/**
 * This tests that we produce valid code when having normal-flow with exceptional edges in blocks.
 * We might perform optimizations that add operations (dup, swap, etc.) before and after
 * instructions that lie on the boundary of the exception table that is generated for a basic block.
 * If live-ranges are minimized this could produce VerifyErrors.
 */
public class TryRangeTestRunner extends TestBase {

  @Test
  public void testRegisterAllocationLimitTrailingRange() throws Exception {
    testForR8(Backend.CF)
        .addProgramClasses(TryRangeTest.class)
        .addKeepMainRule(TryRangeTest.class)
        .setMode(CompilationMode.RELEASE)
        .addDontObfuscate()
        .noTreeShaking()
        .enableInliningAnnotations()
        .addOptionsModification(o -> o.enableLoadStoreOptimization = false)
        .run(TryRangeTest.class)
        .assertSuccessWithOutput(StringUtils.lines("10", "7.0"));
  }

  @Test
  public void testRegisterAllocationLimitLeadingRange() throws Exception {
    CodeInspector inspector =
        testForR8(Backend.CF)
            .addProgramClasses(TryRangeTestLimitRange.class)
            .addKeepMainRule(TryRangeTestLimitRange.class)
            .setMode(CompilationMode.RELEASE)
            .addDontObfuscate()
            .noTreeShaking()
            .enableInliningAnnotations()
            .addOptionsModification(
                o -> {
                  o.enableLoadStoreOptimization = false;
                  o.testing.irModifier = this::processIR;
                })
            .run(TryRangeTestLimitRange.class)
            .assertSuccessWithOutput("")
            .inspector();
    // Assert that we do not have any register-modifying instructions in the throwing block:
    // L0: ; locals:
    // iload 1;
    // invokestatic com.android.tools.r8.cf.TryRangeTestLimitRange.doSomething(I)F
    // L1: ; locals:
    // 11:   pop
    ClassSubject clazz = inspector.clazz("com.android.tools.r8.cf.TryRangeTestLimitRange");
    CfCode cfCode = clazz.uniqueMethodWithOriginalName("main").getMethod().getCode().asCfCode();
    List<CfInstruction> instructions = cfCode.getInstructions();
    CfLabel startLabel = cfCode.getTryCatchRanges().get(0).start;
    int index = 0;
    while (instructions.get(index) != startLabel) {
      index++;
    }
    assert instructions.get(index + 1) instanceof CfLoad;
    assert instructions.get(index + 2) instanceof CfInvoke;
    assert instructions.get(index + 3) == cfCode.getTryCatchRanges().get(0).end;
    assert instructions.get(index + 4) instanceof CfStackInstruction;
  }

  private void processIR(IRCode code, AppView<?> appView) {
    if (!code.method().qualifiedName().equals(TryRangeTestLimitRange.class.getName() + ".main")) {
      return;
    }
    BasicBlock entryBlock = code.entryBlock();
    BasicBlock tryBlock = code.blocks.get(1);
    assertTrue(tryBlock.hasCatchHandlers());
    InstructionListIterator it = entryBlock.listIterator(code);
    Instruction constNumber = it.next();
    while (!constNumber.isConstNumber()) {
      constNumber = it.next();
    }
    it.removeInstructionIgnoreOutValue();
    Instruction add = it.next();
    while (!add.isAdd()) {
      add = it.next();
    }
    it.removeInstructionIgnoreOutValue();
    constNumber.setBlock(tryBlock);
    add.setBlock(tryBlock);
    tryBlock.getInstructions().add(0, add);
    tryBlock.getInstructions().add(0, constNumber);
  }
}
