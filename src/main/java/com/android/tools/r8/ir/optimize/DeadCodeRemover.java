// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class DeadCodeRemover {

  public static void removeDeadCode(
      IRCode code, CodeRewriter codeRewriter, InternalOptions options) {
    Queue<BasicBlock> worklist = new LinkedList<>();
    int color = code.reserveMarkingColor();
    worklist.addAll(code.blocks);
    for (BasicBlock block = worklist.poll(); block != null; block = worklist.poll()) {
      if (block.isMarked(color)) {
        // Ignore marked blocks, as they are scheduled for removal.
        continue;
      }
      removeDeadInstructions(worklist, code, block, options, color);
      removeDeadPhis(worklist, block, options, color);
      removeUnneededCatchHandlers(worklist, block, code, color);
    }
    code.removeMarkedBlocks(color);
    code.returnMarkingColor(color);
    assert code.isConsistentSSA();
    codeRewriter.rewriteMoveResult(code);
  }

  // Add the block from where the value originates to the worklist.
  private static void updateWorklist(Queue<BasicBlock> worklist, Value value, int color) {
    BasicBlock block = null;
    if (value.isPhi()) {
      block = value.asPhi().getBlock();
    } else if (value.definition.hasBlock()) {
      block = value.definition.getBlock();
    }
    if (block != null && !block.isMarked(color)) {
      worklist.add(block);
    }
  }

  // Add all blocks from where the in/debug-values to the instruction originates.
  private static void updateWorklist(
      Queue<BasicBlock> worklist, Instruction instruction, int color) {
    for (Value inValue : instruction.inValues()) {
      updateWorklist(worklist, inValue, color);
    }
    for (Value debugValue : instruction.getDebugValues()) {
      updateWorklist(worklist, debugValue, color);
    }
  }

  private static void removeDeadPhis(Queue<BasicBlock> worklist, BasicBlock block,
      InternalOptions options, int color) {
    Iterator<Phi> phiIt = block.getPhis().iterator();
    while (phiIt.hasNext()) {
      Phi phi = phiIt.next();
      if (phi.isDead(options)) {
        phiIt.remove();
        for (Value operand : phi.getOperands()) {
          operand.removePhiUser(phi);
          updateWorklist(worklist, operand, color);
        }
      }
    }
  }

  private static void removeDeadInstructions(
      Queue<BasicBlock> worklist, IRCode code, BasicBlock block, InternalOptions options,
      int color) {
    InstructionListIterator iterator = block.listIterator(block.getInstructions().size());
    while (iterator.hasPrevious()) {
      Instruction current = iterator.previous();
      // Remove unused invoke results.
      if (current.isInvoke()
          && current.outValue() != null
          && !current.outValue().isUsed()) {
        current.setOutValue(null);
      }
      if (!current.canBeDeadCode(code, options)) {
        continue;
      }
      Value outValue = current.outValue();
      // Instructions with no out value cannot be dead code by the current definition
      // (unused out value). They typically side-effect input values or deals with control-flow.
      assert outValue != null;
      if (!outValue.isDead(options)) {
        continue;
      }
      updateWorklist(worklist, current, color);
      // All users will be removed for this instruction. Eagerly clear them so further inspection
      // of this instruction during dead code elimination will terminate here.
      outValue.clearUsers();
      iterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private static void removeUnneededCatchHandlers(Queue<BasicBlock> worklist, BasicBlock block,
      IRCode code, int color) {
    if (block.hasCatchHandlers() && !block.canThrow()) {
      CatchHandlers<BasicBlock> handlers = block.getCatchHandlers();
      for (BasicBlock target : handlers.getUniqueTargets()) {
        DominatorTree dominatorTree = new DominatorTree(code);
        for (BasicBlock unlinked : block.unlink(target, dominatorTree)) {
          if (!unlinked.isMarked(color)) {
            Iterator<Instruction> iterator = unlinked.iterator();
            while (iterator.hasNext()) {
              updateWorklist(worklist, iterator.next(), color);
            }
            unlinked.mark(color);
          }
        }
      }
    }
  }
}
