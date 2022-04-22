// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural.cf;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.ControlFlowGraph;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.TraversalUtils;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * The following provides a control flow graph for a piece of {@link CfCode}.
 *
 * <p>In the {@link CfControlFlowGraph}, each instruction that is the target of a jump (including
 * fallthrough targets following jumps) starts a new basic block. The first instruction in {@link
 * CfCode} also starts a new block.
 *
 * <p>Each block is identified by the first instruction of the block.
 */
public class CfControlFlowGraph implements ControlFlowGraph<CfBlock, CfInstruction> {

  // Mapping from block entry instructions to cf blocks.
  private final Map<CfInstruction, ? extends CfBlock> blocks;
  private final CfCode code;

  private CfControlFlowGraph(Map<CfInstruction, ? extends CfBlock> blocks, CfCode code) {
    this.blocks = blocks;
    this.code = code;
  }

  private static Builder builder() {
    return new Builder();
  }

  public static CfControlFlowGraph create(CfCode code) {
    return builder().build(code);
  }

  private CfBlock getBlock(CfInstruction blockEntry) {
    assert blocks.containsKey(blockEntry);
    return blocks.get(blockEntry);
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseNormalPredecessors(
      CfBlock block,
      BiFunction<? super CfBlock, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    return TraversalUtils.traverseIterable(block.getPredecessors(), fn, initialValue);
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseExceptionalPredecessors(
      CfBlock block,
      BiFunction<? super CfBlock, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    return TraversalUtils.traverseIterable(block.getExceptionalPredecessors(), fn, initialValue);
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseNormalSuccessors(
      CfBlock block,
      BiFunction<? super CfBlock, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    CfInstruction blockExit = block.getLastInstruction(code);
    CfInstruction fallthroughInstruction = block.getFallthroughInstruction(code);
    return blockExit.traverseNormalTargets(
        (target, value) -> fn.apply(getBlock(target), value), fallthroughInstruction, initialValue);
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseExceptionalSuccessors(
      CfBlock block,
      BiFunction<? super CfBlock, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    return TraversalUtils.traverseIterable(block.getExceptionalSuccessors(), fn, initialValue);
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseInstructions(
      CfBlock block,
      BiFunction<CfInstruction, CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    TraversalContinuation<BT, CT> traversalContinuation =
        TraversalContinuation.doContinue(initialValue);
    for (int instructionIndex = block.getFirstInstructionIndex();
        instructionIndex <= block.getLastInstructionIndex();
        instructionIndex++) {
      CfInstruction instruction = code.getInstructions().get(instructionIndex);
      traversalContinuation = fn.apply(instruction, traversalContinuation.asContinue().getValue());
      if (traversalContinuation.shouldBreak()) {
        break;
      }
    }
    return traversalContinuation;
  }

  private static class Builder {

    CfControlFlowGraph build(CfCode code) {
      // TODO(b/214496607): Implement cfg construction.
      throw new Unimplemented();
    }
  }
}
