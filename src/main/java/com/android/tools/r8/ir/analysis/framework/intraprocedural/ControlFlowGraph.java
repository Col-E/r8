// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.function.BiFunction;

public interface ControlFlowGraph<Block, Instruction> {

  Collection<Block> getPredecessors(Block block);

  Collection<Block> getSuccessors(Block block);

  default boolean hasUniquePredecessor(Block block) {
    return getPredecessors(block).size() == 1;
  }

  default Block getUniquePredecessor(Block block) {
    assert hasUniquePredecessor(block);
    return Iterables.getOnlyElement(getPredecessors(block));
  }

  default boolean hasUniqueSuccessor(Block block) {
    return getSuccessors(block).size() == 1;
  }

  default boolean hasUniqueSuccessorWithUniquePredecessor(Block block) {
    return hasUniqueSuccessor(block) && getPredecessors(getUniqueSuccessor(block)).size() == 1;
  }

  default Block getUniqueSuccessor(Block block) {
    assert hasUniqueSuccessor(block);
    return Iterables.getOnlyElement(getSuccessors(block));
  }

  <BT, CT> TraversalContinuation<BT, CT> traverseInstructions(
      Block block, BiFunction<Instruction, CT, TraversalContinuation<BT, CT>> fn, CT initialValue);
}
