// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Switch;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * Rewrite all branch targets to the destination of trivial goto chains when possible. Does not
 * rewrite fallthrough targets as that would require block reordering and the transformation only
 * makes sense after SSA destruction where there are no phis.
 */
public class TrivialGotosCollapser extends CodeRewriterPass<AppInfo> {

  public TrivialGotosCollapser(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "TrivialGotosCollapser";
  }

  @Override
  protected boolean isAcceptingSSA() {
    return false;
  }

  @Override
  protected boolean isProducingSSA() {
    return false;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    List<BasicBlock> blocksToRemove = new ArrayList<>();
    // Rewrite all non-fallthrough targets to the end of trivial goto chains and remove
    // first round of trivial goto blocks.
    ListIterator<BasicBlock> iterator = code.listIterator();
    assert iterator.hasNext();
    BasicBlock block = iterator.next();
    BasicBlock nextBlock;

    do {
      nextBlock = iterator.hasNext() ? iterator.next() : null;
      if (block.isTrivialGoto()) {
        collapseTrivialGoto(code, block, nextBlock, blocksToRemove);
      } else if (block.exit().isIf()) {
        collapseIfTrueTarget(block);
      } else if (block.exit().isSwitch()) {
        collapseNonFallthroughSwitchTargets(block);
      }
      block = nextBlock;
    } while (nextBlock != null);
    code.removeBlocks(blocksToRemove);
    // Get rid of gotos to the next block.
    while (!blocksToRemove.isEmpty()) {
      blocksToRemove = new ArrayList<>();
      iterator = code.listIterator();
      block = iterator.next();
      do {
        nextBlock = iterator.hasNext() ? iterator.next() : null;
        if (block.isTrivialGoto()) {
          collapseTrivialGoto(code, block, nextBlock, blocksToRemove);
        }
        block = nextBlock;
      } while (block != null);
      code.removeBlocks(blocksToRemove);
    }
    assert removedTrivialGotos(code);
    assert code.isConsistentGraph(appView);
    return CodeRewriterResult.NONE;
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return true;
  }

  public static void unlinkTrivialGotoBlock(BasicBlock block, BasicBlock target) {
    assert block.isTrivialGoto();
    for (BasicBlock pred : block.getPredecessors()) {
      pred.replaceSuccessor(block, target);
    }
    for (BasicBlock succ : block.getSuccessors()) {
      succ.getMutablePredecessors().remove(block);
    }
    for (BasicBlock pred : block.getPredecessors()) {
      if (!target.getPredecessors().contains(pred)) {
        target.getMutablePredecessors().add(pred);
      }
    }
  }

  private boolean isFallthroughBlock(BasicBlock block) {
    for (BasicBlock pred : block.getPredecessors()) {
      if (pred.exit().fallthroughBlock() == block) {
        return true;
      }
    }
    return false;
  }

  private boolean removedTrivialGotos(IRCode code) {
    ListIterator<BasicBlock> iterator = code.listIterator();
    assert iterator.hasNext();
    BasicBlock block = iterator.next();
    BasicBlock nextBlock;
    do {
      nextBlock = iterator.hasNext() ? iterator.next() : null;
      // Trivial goto block are only kept if they are self-targeting or are targeted by
      // fallthroughs.
      BasicBlock blk = block; // Additional local for lambda below.
      assert !block.isTrivialGoto()
          || block.exit().asGoto().getTarget() == block
          || code.entryBlock() == block
          || block.getPredecessors().stream().anyMatch((b) -> b.exit().fallthroughBlock() == blk);
      // Trivial goto blocks never target the next block (in that case there should just be a
      // fallthrough).
      assert !block.isTrivialGoto() || block.exit().asGoto().getTarget() != nextBlock;
      block = nextBlock;
    } while (block != null);
    return true;
  }

  private void collapseTrivialGoto(
      IRCode code, BasicBlock block, BasicBlock nextBlock, List<BasicBlock> blocksToRemove) {

    // This is the base case for GOTO loops.
    if (block.exit().asGoto().getTarget() == block) {
      return;
    }

    BasicBlock target = block.endOfGotoChain();

    if (target == null) {
      // This implies we are in a loop of GOTOs. In that case, we will iteratively remove each
      // trivial GOTO one-by-one until the above base case (one block targeting itself) is left.
      target = block.exit().asGoto().getTarget();
    }

    // Class file target specific checks.
    if (appView.options().isGeneratingClassFiles()) {
      // If the block being looked at for collapsing is a trivial goto, but the successor is a candidate for
      // catch handler recovery (from dex --> java) then we don't want to collapse it.
      // We will want to keep this block so that we can handle popping the exception off the stack later.
      if (block.isEventuallySuccessorOfUnmovedException() &&
              (block.isCatchDelegateCandidate() || block.isOnlySuccessorCatchDelegateCandidate()))
        return;
    }

    boolean needed = false;
    if (target != nextBlock) {
      // Not targeting the fallthrough block, determine if we need this goto. We need it if
      // a fallthrough can hit this block. That is the case if the block is the entry block
      // or if one of the predecessors fall through to the block.
      needed = code.entryBlock() == block || isFallthroughBlock(block);
    }

    if (!needed) {
      blocksToRemove.add(block);
      unlinkTrivialGotoBlock(block, target);
    }
  }

  private void collapseIfTrueTarget(BasicBlock block) {
    If insn = block.exit().asIf();
    BasicBlock target = insn.getTrueTarget();
    BasicBlock newTarget = target.endOfGotoChain();
    BasicBlock fallthrough = insn.fallthroughBlock();
    BasicBlock newFallthrough = fallthrough.endOfGotoChain();
    if (newTarget != null && target != newTarget) {
      insn.getBlock().replaceSuccessor(target, newTarget);
      target.getMutablePredecessors().remove(block);
      if (!newTarget.getPredecessors().contains(block)) {
        newTarget.getMutablePredecessors().add(block);
      }
    }
    if (block.exit().isIf()) {
      insn = block.exit().asIf();
      if (insn.getTrueTarget() == newFallthrough) {
        // Replace if with the same true and fallthrough target with a goto to the fallthrough.
        block.replaceSuccessor(insn.getTrueTarget(), fallthrough);
        assert block.exit().isGoto();
        assert block.exit().asGoto().getTarget() == fallthrough;
      }
    }
  }

  private void collapseNonFallthroughSwitchTargets(BasicBlock block) {
    Switch insn = block.exit().asSwitch();
    BasicBlock fallthroughBlock = insn.fallthroughBlock();
    Set<BasicBlock> replacedBlocks = new HashSet<>();
    for (int j = 0; j < insn.targetBlockIndices().length; j++) {
      BasicBlock target = insn.targetBlock(j);
      if (target != fallthroughBlock) {
        BasicBlock newTarget = target.endOfGotoChain();
        if (newTarget != null && target != newTarget && !replacedBlocks.contains(target)) {
          insn.getBlock().replaceSuccessor(target, newTarget);
          target.getMutablePredecessors().remove(block);
          if (!newTarget.getPredecessors().contains(block)) {
            newTarget.getMutablePredecessors().add(block);
          }
          replacedBlocks.add(target);
        }
      }
    }
  }
}
