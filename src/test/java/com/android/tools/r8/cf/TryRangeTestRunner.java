// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import java.util.ListIterator;
import org.junit.Test;

/**
 * This tests that we produce valid code when having normal-flow with exceptional edges in blocks.
 * We might perform optimizations that add operations (dup, swap, etc.) before and after
 * instructions that lie on the boundary of the exception table that is generated for a basic block.
 * If live-ranges are minimized this could produce VerifyErrors. TODO(b/119771771) Will fail if
 * shorten live ranges without shorten exception table range.
 */
public class TryRangeTestRunner extends TestBase {

  @Test
  public void testRegisterAllocationLimitTrailingRange() throws Exception {
    testForR8(Backend.CF)
        .addProgramClasses(TryRangeTest.class)
        .addKeepMainRule(TryRangeTest.class)
        .setMode(CompilationMode.RELEASE)
        .minification(false)
        .noTreeShaking()
        .enableInliningAnnotations()
        .addOptionsModification(
            o -> {
              o.testing.disallowLoadStoreOptimization = true;
            })
        .run(TryRangeTest.class)
        .assertSuccess();
  }

  @Test
  public void testRegisterAllocationLimitLeadingRange() throws Exception {
    testForR8(Backend.CF)
        .addProgramClasses(TryRangeTestLimitRange.class)
        .addKeepMainRule(TryRangeTestLimitRange.class)
        .setMode(CompilationMode.RELEASE)
        .minification(false)
        .noTreeShaking()
        .enableInliningAnnotations()
        .addOptionsModification(
            o -> {
              o.testing.disallowLoadStoreOptimization = true;
              o.testing.irModifier = this::processIR;
              // TODO(mkroghj) Remove this option entirely when splittingExceptionalEdges is moved.
              o.testing.noSplittingExceptionalEdges = true;
            })
        .run(TryRangeTestLimitRange.class)
        .assertFailure();
  }

  private void processIR(IRCode code) {
    if (!code.method.qualifiedName().equals(TryRangeTestLimitRange.class.getName() + ".main")) {
      return;
    }
    BasicBlock entryBlock = code.blocks.get(0);
    BasicBlock tryBlock = code.blocks.get(1);
    assertTrue(tryBlock.hasCatchHandlers());
    ListIterator<Instruction> it = entryBlock.getInstructions().listIterator();
    Instruction constNumber = it.next();
    while (!constNumber.isConstNumber()) {
      constNumber = it.next();
    }
    it.remove();
    Instruction add = it.next();
    while (!add.isAdd()) {
      add = it.next();
    }
    it.remove();
    constNumber.setBlock(tryBlock);
    add.setBlock(tryBlock);
    tryBlock.getInstructions().add(0, add);
    tryBlock.getInstructions().add(0, constNumber);
  }
}
