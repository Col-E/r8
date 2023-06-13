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
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InitClass;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueIsDeadAnalysis;
import com.android.tools.r8.ir.conversion.passes.BranchSimplifier;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
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

  public CodeRewriter getCodeRewriter() {
    return codeRewriter;
  }

  public void run(IRCode code, Timing timing) {
    timing.begin("Remove dead code");

    codeRewriter.rewriteMoveResult(code);

    BranchSimplifier branchSimplifier = new BranchSimplifier(appView);

    // We may encounter unneeded catch handlers after each iteration, e.g., if a dead instruction
    // is the only throwing instruction in a block. Removing unneeded catch handlers can lead to
    // more dead instructions.
    Deque<BasicBlock> worklist = new ArrayDeque<>();
    do {
      ValueIsDeadAnalysis valueIsDeadAnalysis = new ValueIsDeadAnalysis(appView, code);
      worklist.addAll(code.topologicallySortedBlocks());
      while (!worklist.isEmpty()) {
        BasicBlock block = worklist.removeLast();
        removeDeadInstructions(worklist, code, block, valueIsDeadAnalysis);
        removeDeadPhis(worklist, block, valueIsDeadAnalysis);
      }
    } while (branchSimplifier.simplifyIf(code).anySimplifications()
        || removeUnneededCatchHandlers(code));

    code.removeRedundantBlocks();
    assert code.isConsistentSSA(appView);

    timing.end();
  }

  public boolean verifyNoDeadCode(IRCode code) {
    assert !codeRewriter.rewriteMoveResult(code);
    assert !removeUnneededCatchHandlers(code);
    ValueIsDeadAnalysis valueIsDeadAnalysis = new ValueIsDeadAnalysis(appView, code);
    for (BasicBlock block : code.blocks) {
      assert !valueIsDeadAnalysis.hasDeadPhi(block);
      for (Instruction instruction : block.getInstructions()) {
        // No unused move-result instructions.
        assert !instruction.isInvoke()
            || !instruction.hasOutValue()
            || instruction.outValue().hasAnyUsers();
        // No dead instructions.
        assert !instruction.canBeDeadCode(appView, code).isDeadIfOutValueIsDead()
            || (instruction.hasOutValue() && !valueIsDeadAnalysis.isDead(instruction.outValue()));
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

  private void removeDeadPhis(
      Queue<BasicBlock> worklist, BasicBlock block, ValueIsDeadAnalysis valueIsDeadAnalysis) {
    Iterator<Phi> phiIt = block.getPhis().iterator();
    while (phiIt.hasNext()) {
      Phi phi = phiIt.next();
      if (valueIsDeadAnalysis.isDead(phi)) {
        phiIt.remove();
        for (Value operand : phi.getOperands()) {
          operand.removePhiUser(phi);
          updateWorklist(worklist, operand);
        }
      }
    }
  }

  private void removeDeadInstructions(
      Queue<BasicBlock> worklist,
      IRCode code,
      BasicBlock block,
      ValueIsDeadAnalysis valueIsDeadAnalysis) {
    InstructionListIterator iterator = block.listIterator(code, block.getInstructions().size());
    while (iterator.hasPrevious()) {
      Instruction current = iterator.previous();
      if (current.hasOutValue()) {
        // Replace unnecessary cast values.
        if (current.isCheckCast()) {
          CheckCast checkCast = current.asCheckCast();
          if (!checkCast.isRefiningStaticType(appView.options())
              && checkCast.outValue().getLocalInfo() == checkCast.object().getLocalInfo()) {
            checkCast.outValue().replaceUsers(checkCast.object());
            checkCast.object().uniquePhiUsers().forEach(Phi::removeTrivialPhi);
          }
        }
        // Remove unused invoke results.
        if (current.isInvoke() && !current.outValue().isUsed()) {
          current.setOutValue(null);
        }
        if (current.isStaticGet() && !current.outValue().isUsed() && appView.hasLiveness()) {
          Box<InitClass> initClass = new Box<>();
          if (iterator.removeOrReplaceCurrentInstructionByInitClassIfPossible(
              appView.withLiveness(),
              code,
              current.asStaticGet().getField().getHolderType(),
              initClass::set)) {
            if (initClass.isSet()) {
              // Apply dead code remover to the new init-class instruction.
              current = iterator.previous();
              assert current == initClass.get();
            } else {
              // Instruction removed.
              continue;
            }
          }
        }
      }
      DeadInstructionResult deadInstructionResult = current.canBeDeadCode(appView, code);
      if (deadInstructionResult.isNotDead()) {
        continue;
      }
      if (deadInstructionResult.isMaybeDead()) {
        boolean satisfied = true;
        for (Value valueRequiredToBeDead : deadInstructionResult.getValuesRequiredToBeDead()) {
          if (!valueIsDeadAnalysis.isDead(valueRequiredToBeDead)) {
            satisfied = false;
            break;
          }
        }
        if (!satisfied) {
          continue;
        }
      }
      Value outValue = current.outValue();
      if (outValue != null && !valueIsDeadAnalysis.isDead(outValue)) {
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
    assert code.isConsistentGraph(appView);
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
      if (appInfoWithLiveness != null) {
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
          return IterableUtils.singleton(inValueRequiredToBeDead);
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
