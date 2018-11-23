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

  private static List<BasicBlockPeephole> nonDestructivePeepholes() {
    return ImmutableList.of(new MoveLoadUpPeephole(), new StoreLoadPeephole());
  }

  // The StoreLoadPeephole and StoreSequenceLoadPeephole are non-destructive but we would like it
  // to run in a fix-point with the other peepholes to allow for more matches.
  private static List<BasicBlockPeephole> destructivePeepholes() {
    return ImmutableList.of(
        new StoreSequenceLoadPeephole(),
        new StoreLoadPeephole(),
        new LoadLoadDupPeephole(),
        new DupDupDupPeephole(),
        new StoreLoadToDupStorePeephole());
  }

  public static void optimize(IRCode code) {
    runPeepholes(code, nonDestructivePeepholes());
    runPeepholes(code, destructivePeepholes());
  }

  private static void runPeepholes(IRCode code, List<BasicBlockPeephole> peepholes) {
    ListIterator<BasicBlock> blocksIterator = code.blocks.listIterator(code.blocks.size());
    while (blocksIterator.hasPrevious()) {
      BasicBlock currentBlock = blocksIterator.previous();
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
          boolean localMatch = peepHole.match(it);
          if (localMatch && peepHole.resetAfterMatch()) {
            it =
                new LinearFlowInstructionIterator(
                    currentBlock, currentBlock.getInstructions().size());
          } else {
            matched |= localMatch;
          }
        }
        if (it.hasPrevious()) {
          it.previous();
        }
      }
    }
  }
}
