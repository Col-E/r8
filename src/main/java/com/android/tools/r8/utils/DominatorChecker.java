// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ir.code.BasicBlock;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Set;

/**
 * Given a subgraph defined by sourceBlock / destBlock, where sourceBlock dominates destBlock,
 * allows querying whether other blocks within this subgraph dominate destBlock.
 */
public interface DominatorChecker {
  boolean check(BasicBlock targetBlock);

  DominatorChecker TRUE_CHECKER = targetBlock -> true;
  DominatorChecker FALSE_CHECKER = targetBlock -> false;

  class PrecomputedDominatorChecker implements DominatorChecker {
    private final Set<BasicBlock> dominators;

    public PrecomputedDominatorChecker(Set<BasicBlock> dominators) {
      this.dominators = dominators;
    }

    @Override
    public boolean check(BasicBlock targetBlock) {
      return dominators.contains(targetBlock);
    }
  }

  class TraversingDominatorChecker implements DominatorChecker {
    private final BasicBlock sourceBlock;
    private final BasicBlock destBlock;
    private final Set<BasicBlock> knownDominators;
    private final ArrayDeque<BasicBlock> workQueue = new ArrayDeque<>();
    private final Set<BasicBlock> visited;
    private BasicBlock prevTargetBlock;

    private TraversingDominatorChecker(
        BasicBlock sourceBlock, BasicBlock destBlock, Set<BasicBlock> knownDominators) {
      this.sourceBlock = sourceBlock;
      this.destBlock = destBlock;
      this.knownDominators = knownDominators;
      this.visited = Sets.newIdentityHashSet();
      prevTargetBlock = destBlock;
    }

    @Override
    public boolean check(BasicBlock targetBlock) {
      assert prevTargetBlock != null : "DominatorChecker cannot be used after returning false.";
      Set<BasicBlock> knownDominators = this.knownDominators;
      if (knownDominators.contains(targetBlock)) {
        return true;
      }
      // See if a block on the same linear path has already been checked.
      BasicBlock firstSplittingBlock = targetBlock;
      if (firstSplittingBlock.hasUniqueSuccessor()) {
        do {
          // knownDominators prevents firstSplittingBlock from being destBlock.
          assert firstSplittingBlock != destBlock;
          firstSplittingBlock = firstSplittingBlock.getUniqueSuccessor();
        } while (firstSplittingBlock.hasUniqueSuccessor());

        if (knownDominators.contains(firstSplittingBlock)) {
          knownDominators.add(targetBlock);
          return true;
        }
      }

      boolean ret;
      // Since we know the previously checked block is a dominator, narrow the check by using it for
      // either sourceBlock or destBlock.
      if (visited.contains(targetBlock)) {
        visited.clear();
        ret =
            checkWithTraversal(prevTargetBlock, destBlock, firstSplittingBlock, visited, workQueue);
        prevTargetBlock = firstSplittingBlock;
      } else {
        ret = checkWithTraversal(sourceBlock, prevTargetBlock, targetBlock, visited, workQueue);
        prevTargetBlock = targetBlock;
      }
      if (ret) {
        knownDominators.add(targetBlock);
        if (firstSplittingBlock != targetBlock) {
          knownDominators.add(firstSplittingBlock);
        }
      } else {
        prevTargetBlock = null;
      }
      return ret;
    }

    private static boolean checkWithTraversal(
        BasicBlock sourceBlock,
        BasicBlock destBlock,
        BasicBlock targetBlock,
        Set<BasicBlock> visited,
        ArrayDeque<BasicBlock> workQueue) {
      assert workQueue.isEmpty();

      visited.add(targetBlock);

      workQueue.addAll(destBlock.getPredecessors());
      do {
        BasicBlock curBlock = workQueue.removeLast();
        if (!visited.add(curBlock)) {
          continue;
        }
        if (curBlock == sourceBlock) {
          // There is a path from sourceBlock -> destBlock that does not go through block.
          return false;
        }
        assert !curBlock.isEntry() : "sourceBlock did not dominate destBlock";
        workQueue.addAll(curBlock.getPredecessors());
      } while (!workQueue.isEmpty());

      return true;
    }
  }

  static DominatorChecker create(BasicBlock sourceBlock, BasicBlock destBlock) {
    // Fast-path: blocks are the same.
    // As of Nov 2023: in Chrome for String.format() optimization, this covers 77% of cases.
    if (sourceBlock == destBlock) {
      return new PrecomputedDominatorChecker(Collections.singleton(sourceBlock));
    }

    // Shrink the subgraph by moving sourceBlock forward to the first block with multiple
    // successors.
    Set<BasicBlock> headAndTailDominators = Sets.newIdentityHashSet();
    headAndTailDominators.add(sourceBlock);
    while (sourceBlock.hasUniqueSuccessor()) {
      sourceBlock = sourceBlock.getUniqueSuccessor();
      if (!headAndTailDominators.add(sourceBlock)) {
        // Hit an infinite loop. Code would not verify in this case.
        assert false;
        return FALSE_CHECKER;
      }
      if (sourceBlock == destBlock) {
        // As of Nov 2023: in Chrome for String.format() optimization, a linear path from
        // source->dest was 14% of cases.
        return new PrecomputedDominatorChecker(headAndTailDominators);
      }
    }
    if (sourceBlock.getSuccessors().isEmpty()) {
      return FALSE_CHECKER;
    }

    // Shrink the subgraph by moving destBlock back to the first block with multiple predecessors.
    headAndTailDominators.add(destBlock);
    while (destBlock.hasUniquePredecessor()) {
      destBlock = destBlock.getUniquePredecessor();
      if (!headAndTailDominators.add(destBlock)) {
        if (sourceBlock == destBlock) {
          // This normally happens when moving sourceBlock forwards, but when moving destBlock
          // backwards when sourceBlock has multiple successors.
          return new PrecomputedDominatorChecker(headAndTailDominators);
        }
        // Hit an infinite loop. Code would not verify in this case.
        assert false;
        return FALSE_CHECKER;
      }
    }

    if (destBlock.isEntry()) {
      return FALSE_CHECKER;
    }

    return new TraversingDominatorChecker(sourceBlock, destBlock, headAndTailDominators);
  }
}
