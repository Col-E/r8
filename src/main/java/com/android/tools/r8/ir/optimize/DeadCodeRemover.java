// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.CatchHandlers.CatchHandler;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.Queue;

public class DeadCodeRemover {

  private final AppView<?> appView;
  private final CodeRewriter codeRewriter;

  public DeadCodeRemover(AppView<?> appView, CodeRewriter codeRewriter) {
    this.appView = appView;
    this.codeRewriter = codeRewriter;
  }

  public void run(IRCode code, Timing timing) {
    timing.begin("Remove dead code");

    codeRewriter.rewriteMoveResult(code);

    // We may encounter unneeded catch handlers after each iteration, e.g., if a dead instruction
    // is the only throwing instruction in a block. Removing unneeded catch handlers can lead to
    // more dead instructions.
    Deque<BasicBlock> worklist = new ArrayDeque<>();
    do {
      worklist.addAll(code.topologicallySortedBlocks());
      while (!worklist.isEmpty()) {
        BasicBlock block = worklist.removeLast();
        removeDeadInstructions(worklist, code, block);
        removeDeadPhis(worklist, code, block);
      }
    } while (removeUnneededCatchHandlers(code));
    assert code.isConsistentSSA();

    timing.end();
  }

  public boolean verifyNoDeadCode(IRCode code) {
    assert !codeRewriter.rewriteMoveResult(code);
    assert !removeUnneededCatchHandlers(code);
    for (BasicBlock block : code.blocks) {
      assert !block.hasDeadPhi(appView, code);
      for (Instruction instruction : block.getInstructions()) {
        // No unused move-result instructions.
        assert !instruction.isInvoke()
            || !instruction.hasOutValue()
            || instruction.outValue().hasAnyUsers();
        // No dead instructions.
        assert !instruction.canBeDeadCode(appView, code).isDeadIfOutValueIsDead()
            || (instruction.hasOutValue() && !instruction.outValue().isDead(appView, code));
      }
    }
    return true;
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

  private void removeDeadPhis(Queue<BasicBlock> worklist, IRCode code, BasicBlock block) {
    Iterator<Phi> phiIt = block.getPhis().iterator();
    while (phiIt.hasNext()) {
      Phi phi = phiIt.next();
      if (phi.isDead(appView, code)) {
        phiIt.remove();
        for (Value operand : phi.getOperands()) {
          operand.removePhiUser(phi);
          updateWorklist(worklist, operand);
        }
      }
    }
  }

  private void removeDeadInstructions(Queue<BasicBlock> worklist, IRCode code, BasicBlock block) {
    InstructionListIterator iterator = block.listIterator(code, block.getInstructions().size());
    while (iterator.hasPrevious()) {
      Instruction current = iterator.previous();
      // Remove unused invoke results.
      if (current.isInvoke() && current.hasOutValue() && !current.outValue().isUsed()) {
        current.setOutValue(null);
      }
      DeadInstructionResult deadInstructionResult = current.canBeDeadCode(appView, code);
      if (deadInstructionResult.isNotDead()) {
        continue;
      }
      if (deadInstructionResult.isMaybeDead()) {
        boolean satisfied = true;
        for (Value valueRequiredToBeDead : deadInstructionResult.getValuesRequiredToBeDead()) {
          if (!valueRequiredToBeDead.isDead(appView, code)) {
            satisfied = false;
            break;
          }
        }
        if (!satisfied) {
          continue;
        }
      }
      Value outValue = current.outValue();
      if (outValue != null && !outValue.isDead(appView, code)) {
        continue;
      }
      updateWorklist(worklist, current);
      // All users will be removed for this instruction. Eagerly clear them so further inspection
      // of this instruction during dead code elimination will terminate here.
      if (outValue != null) {
        outValue.clearUsers();
      }
      iterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private boolean removeUnneededCatchHandlers(IRCode code) {
    boolean mayHaveIntroducedUnreachableBlocks = false;
    for (BasicBlock block : code.blocks) {
      if (block.hasCatchHandlers()) {
        if (block.canThrow()) {
          if (appView.enableWholeProgramOptimizations()) {
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
    return mayHaveIntroducedUnreachableBlocks;
  }

  /**
   * Returns the catch handlers of the given block that are dead, if any.
   */
  private Collection<CatchHandler<BasicBlock>> getDeadCatchHandlers(BasicBlock block) {
    AppInfoWithLiveness appInfoWithLiveness = appView.appInfo().withLiveness();
    ImmutableList.Builder<CatchHandler<BasicBlock>> builder = ImmutableList.builder();
    CatchHandlers<BasicBlock> catchHandlers = block.getCatchHandlers();
    for (int i = 0; i < catchHandlers.size(); ++i) {
      DexType guard = catchHandlers.getGuards().get(i);
      BasicBlock target = catchHandlers.getAllTargets().get(i);

      // We can exploit subtyping information to eliminate a catch handler if the guard is
      // subsumed by a previous guard.
      boolean isSubsumedByPreviousGuard = false;
      for (int j = 0; j < i; ++j) {
        DexType previousGuard = catchHandlers.getGuards().get(j);
        if (appView.isSubtype(guard, previousGuard).isTrue()) {
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
      if (appInfoWithLiveness != null && appView.options().enableUninstantiatedTypeOptimization) {
        DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(guard));
        if (clazz != null && !appInfoWithLiveness.isInstantiatedDirectlyOrIndirectly(clazz)) {
          builder.add(new CatchHandler<>(guard, target));
          continue;
        }
      }
    }
    return builder.build();
  }

  public abstract static class DeadInstructionResult {

    private static final DeadInstructionResult DEFINITELY_DEAD_INSTANCE =
        new DeadInstructionResult() {
          @Override
          public boolean isDeadIfOutValueIsDead() {
            return true;
          }
        };

    private static final DeadInstructionResult DEFINITELY_NOT_DEAD_INSTANCE =
        new DeadInstructionResult() {
          @Override
          public boolean isNotDead() {
            return true;
          }
        };

    public static DeadInstructionResult deadIfOutValueIsDead() {
      return DEFINITELY_DEAD_INSTANCE;
    }

    public static DeadInstructionResult notDead() {
      return DEFINITELY_NOT_DEAD_INSTANCE;
    }

    public static DeadInstructionResult deadIfInValueIsDead(Value inValueRequiredToBeDead) {
      return new DeadInstructionResult() {
        @Override
        public boolean isMaybeDead() {
          return true;
        }

        @Override
        public Iterable<Value> getValuesRequiredToBeDead() {
          return () -> Iterators.singletonIterator(inValueRequiredToBeDead);
        }
      };
    }

    public boolean isDeadIfOutValueIsDead() {
      return false;
    }

    public boolean isNotDead() {
      return false;
    }

    public boolean isMaybeDead() {
      return false;
    }

    public Iterable<Value> getValuesRequiredToBeDead() {
      throw new Unreachable();
    }
  }
}
