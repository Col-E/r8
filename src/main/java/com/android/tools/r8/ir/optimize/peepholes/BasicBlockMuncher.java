// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.peepholes;

import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.LinearFlowInstructionIterator;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.ListIterator;

public class BasicBlockMuncher {

  private final List<BasicBlockPeephole> nonDestructivePeepholes =
      ImmutableList.of(new MoveLoadUpPeephole());

  // The StoreLoadPeephole is non-destructive but we would like it to run in a fix-point with the
  // other peepholes to allow for more matches.
  private final List<BasicBlockPeephole> destructivePeepholes =
      ImmutableList.of(
          new StoreSequenceLoadPeephole(),
          new StoreLoadPeephole(),
          new LoadLoadDupPeephole(),
          new DupDupDupPeephole());

  private final List<List<BasicBlockPeephole>> allPeepholes =
      ImmutableList.of(nonDestructivePeepholes, destructivePeepholes);

  public void optimize(IRCode code) {
    ListIterator<BasicBlock> blocksIterator = code.blocks.listIterator(code.blocks.size());
    while (blocksIterator.hasPrevious()) {
      BasicBlock currentBlock = blocksIterator.previous();
      for (List<BasicBlockPeephole> peepholes : allPeepholes) {
        InstructionListIterator it =
            new LinearFlowInstructionIterator(currentBlock, currentBlock.getInstructions().size());
        boolean matched = false;
        while (matched || it.hasPrevious()) {
          if (!it.hasPrevious()) {
            matched = false;
            it =
                new LinearFlowInstructionIterator(
                    currentBlock, currentBlock.getInstructions().size());
          }
          for (BasicBlockPeephole peepHole : peepholes) {
            matched |= peepHole.match(it);
          }
          if (it.hasPrevious()) {
            it.previous();
          }
        }
      }
    }
  }
}
