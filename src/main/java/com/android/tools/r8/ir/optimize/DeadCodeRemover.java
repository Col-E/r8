// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.CatchHandlers.CatchHandler;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class DeadCodeRemover {

  private final AppInfo appInfo;
  private final CodeRewriter codeRewriter;
  private final GraphLense graphLense;
  private final InternalOptions options;
  private final boolean enableWholeProgramOptimizations;

  public DeadCodeRemover(
      AppInfo appInfo,
      CodeRewriter codeRewriter,
      GraphLense graphLense,
      InternalOptions options,
      boolean enableWholeProgramOptimizations) {
    this.appInfo = appInfo;
    this.codeRewriter = codeRewriter;
    this.graphLense = graphLense;
    this.options = options;
    this.enableWholeProgramOptimizations = enableWholeProgramOptimizations;
  }

  public void run(IRCode code) {
    removeUnneededCatchHandlers(code);
    Queue<BasicBlock> worklist = new LinkedList<>();
    worklist.addAll(code.blocks);
    for (BasicBlock block = worklist.poll(); block != null; block = worklist.poll()) {
      removeDeadInstructions(worklist, code, block, options);
      removeDeadPhis(worklist, block, options);
    }
    // We may encounter unneeded catch handlers again, e.g., if a dead instruction (due to
    // const-string canonicalization for example) is the only throwing instruction in a block.
    removeUnneededCatchHandlers(code);
    assert code.isConsistentSSA();
    codeRewriter.rewriteMoveResult(code);
  }

  // Add the block from where the value originates to the worklist.
  private static void updateWorklist(Queue<BasicBlock> worklist, Value value) {
    BasicBlock block = null;
    if (value.isPhi()) {
      block = value.asPhi().getBlock();
    } else if (value.definition.hasBlock()) {
      block = value.definition.getBlock();
    }
    if (block != null) {
      worklist.add(block);
    }
  }

  // Add all blocks from where the in/debug-values to the instruction originates.
  private static void updateWorklist(Queue<BasicBlock> worklist, Instruction instruction) {
    for (Value inValue : instruction.inValues()) {
      updateWorklist(worklist, inValue);
    }
    for (Value debugValue : instruction.getDebugValues()) {
      updateWorklist(worklist, debugValue);
    }
  }

  private static void removeDeadPhis(
      Queue<BasicBlock> worklist, BasicBlock block, InternalOptions options) {
    Iterator<Phi> phiIt = block.getPhis().iterator();
    while (phiIt.hasNext()) {
      Phi phi = phiIt.next();
      if (phi.isDead(options)) {
        phiIt.remove();
        for (Value operand : phi.getOperands()) {
          operand.removePhiUser(phi);
          updateWorklist(worklist, operand);
        }
      }
    }
  }

  private static void removeDeadInstructions(
      Queue<BasicBlock> worklist, IRCode code, BasicBlock block, InternalOptions options) {
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
      updateWorklist(worklist, current);
      // All users will be removed for this instruction. Eagerly clear them so further inspection
      // of this instruction during dead code elimination will terminate here.
      outValue.clearUsers();
      iterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private void removeUnneededCatchHandlers(IRCode code) {
    boolean mayHaveIntroducedUnreachableBlocks = false;
    for (BasicBlock block : code.blocks) {
      if (block.hasCatchHandlers()) {
        if (block.canThrow()) {
          if (enableWholeProgramOptimizations) {
            Collection<CatchHandler<BasicBlock>> deadCatchHandlers = getDeadCatchHandlers(block);
            if (!deadCatchHandlers.isEmpty()) {
              for (CatchHandler<BasicBlock> catchHandler : deadCatchHandlers) {
                catchHandler.target.unlinkCatchHandlerForGuard(catchHandler.guard);
              }
              mayHaveIntroducedUnreachableBlocks = true;
            }
          }
        } else {
          CatchHandlers<BasicBlock> handlers = block.getCatchHandlers();
          for (BasicBlock target : handlers.getUniqueTargets()) {
            target.unlinkCatchHandler();
            mayHaveIntroducedUnreachableBlocks = true;
          }
        }
      }
    }
    if (mayHaveIntroducedUnreachableBlocks) {
      code.removeUnreachableBlocks();
    }
    assert code.isConsistentGraph();
  }

  /**
   * Returns the catch handlers of the given block that are dead, if any.
   */
  private Collection<CatchHandler<BasicBlock>> getDeadCatchHandlers(BasicBlock block) {
    AppInfoWithLiveness appInfoWithLiveness = appInfo.withLiveness();
    ImmutableList.Builder<CatchHandler<BasicBlock>> builder = ImmutableList.builder();
    CatchHandlers<BasicBlock> catchHandlers = block.getCatchHandlers();
    for (int i = 0; i < catchHandlers.size(); ++i) {
      DexType guard = catchHandlers.getGuards().get(i);
      BasicBlock target = catchHandlers.getAllTargets().get(i);
      if (guard == DexItemFactory.catchAllType) {
        continue;
      }

      // We can exploit subtyping information to eliminate a catch handler if the guard is
      // subsumed by a previous guard.
      boolean isSubsumedByPreviousGuard = false;
      for (int j = 0; j < i; ++j) {
        DexType previousGuard = catchHandlers.getGuards().get(j);
        if (guard.isSubtypeOf(previousGuard, appInfo)) {
          isSubsumedByPreviousGuard = true;
          break;
        }
      }
      if (isSubsumedByPreviousGuard) {
        builder.add(new CatchHandler<>(guard, target));
        continue;
      }

      // We can exploit that a catch handler must be dead if its guard is never instantiated
      // directly or indirectly.
      if (appInfoWithLiveness != null) {
        DexClass clazz = appInfo.definitionFor(guard);
        if (clazz != null
            && clazz.isProgramClass()
            && !appInfoWithLiveness.isInstantiatedDirectlyOrIndirectly(guard)) {
          builder.add(new CatchHandler<>(guard, target));
          continue;
        }
      }
    }
    return builder.build();
  }
}
