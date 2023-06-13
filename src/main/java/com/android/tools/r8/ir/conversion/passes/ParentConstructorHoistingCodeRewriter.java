// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Pass that attempts to hoist the parent constructor call inside constructors to avoid
 * instance-puts to the unininitialized this, as this enables more aggressive inlining of
 * constructors.
 */
// TODO(b/278975138): Do not remove verification errors from hoisting.
public class ParentConstructorHoistingCodeRewriter
    extends CodeRewriterPass<AppInfoWithClassHierarchy> {

  private List<InvokeDirect> sideEffectFreeConstructorCalls;

  public ParentConstructorHoistingCodeRewriter(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getTimingId() {
    return "Parent constructor hoisting pass";
  }

  @Override
  protected void rewriteCode(IRCode code) {
    for (InvokeDirect invoke : getOrComputeSideEffectFreeConstructorCalls(code)) {
      hoistSideEffectFreeConstructorCall(code, invoke);
    }
  }

  private void hoistSideEffectFreeConstructorCall(IRCode code, InvokeDirect invoke) {
    Deque<Instruction> constants = new ArrayDeque<>();
    // TODO(b/281975599): This loop would not be needed if we did not have any trivial gotos.
    while (true) {
      hoistSideEffectFreeConstructorCallInCurrentBlock(code, invoke, constants);
      Instruction firstHoistedInstruction = CollectionUtils.getFirstOrDefault(constants, invoke);
      if (invoke.getBlock().entry() != firstHoistedInstruction
          || !hoistSideEffectFreeConstructorCallIntoPredecessorBlock(code, invoke, constants)) {
        break;
      }
    }
  }

  // TODO(b/278975138): Instead of hoisting constructor call one instruction up at a time as a
  //  peephole optimization, consider finding the insertion position and then modifying the IR once.
  private void hoistSideEffectFreeConstructorCallInCurrentBlock(
      IRCode code, InvokeDirect invoke, Deque<Instruction> constants) {
    InstructionListIterator instructionIterator = invoke.getBlock().listIterator(code);
    instructionIterator.positionBeforeNextInstruction(
        CollectionUtils.getFirstOrDefault(constants, invoke));
    while (instructionIterator.hasPrevious()) {
      Instruction previousInstruction = instructionIterator.previous();
      if (previousInstruction.isArgument()) {
        // Cannot hoist the constructor call above the Argument instruction.
        return;
      }
      if (previousInstruction.hasOutValue()
          && invoke.inValues().contains(previousInstruction.outValue())) {
        if (previousInstruction.isConstNumber() || previousInstruction.isConstString()) {
          // Record that the constant instruction should be hoisted along with the constructor call.
          constants.addFirst(previousInstruction);
          continue;
        }
        // Cannot hoist the constructor call above the definition of a value that is used as an
        // argument to the constructor call.
        return;
      }
      // Change the instruction order and continue the hoisting.
      List<Instruction> newInstructionOrder =
          ImmutableList.<Instruction>builderWithExpectedSize(constants.size() + 2)
              .addAll(constants)
              .add(invoke)
              .add(previousInstruction)
              .build();
      instructionIterator.next();
      instructionIterator.set(newInstructionOrder);
      IteratorUtils.skip(instructionIterator, -newInstructionOrder.size() - 1);
    }
  }

  private boolean hoistSideEffectFreeConstructorCallIntoPredecessorBlock(
      IRCode code, InvokeDirect invoke, Deque<Instruction> constants) {
    BasicBlock block = invoke.getBlock();
    if (!block.hasUniquePredecessor()) {
      return false;
    }

    BasicBlock predecessorBlock = block.getUniquePredecessor();
    if (!predecessorBlock.hasUniqueSuccessor() || predecessorBlock.hasCatchHandlers()) {
      return false;
    }

    // Remove the constants and the invoke from the block.
    constants.forEach(constant -> block.getInstructions().removeFirst());
    block.getInstructions().removeFirst();

    // Add the constants and the invoke before the exit instruction in the predecessor block.
    InstructionListIterator predecessorInstructionIterator =
        predecessorBlock.listIterator(code, predecessorBlock.getInstructions().size() - 1);
    constants.forEach(predecessorInstructionIterator::add);
    predecessorInstructionIterator.add(invoke);

    // Position the predecessor instruction iterator right before the first instruction that is
    // subject to hoisting.
    IteratorUtils.skip(predecessorInstructionIterator, -constants.size() - 1);
    assert predecessorInstructionIterator.peekNext()
        == CollectionUtils.getFirstOrDefault(constants, invoke);
    return true;
  }

  /** Only run this when the rewriting may actually enable more constructor inlining. */
  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    if (!appView.hasClassHierarchy()) {
      return false;
    }
    ProgramMethod context = code.context();
    if (!context.getDefinition().isInstanceInitializer()
        || !options.canInitNewInstanceUsingSuperclassConstructor()) {
      return false;
    }
    KeepMethodInfo keepInfo = appView.getKeepInfo(context);
    return keepInfo.isOptimizationAllowed(options)
        && keepInfo.isShrinkingAllowed(options)
        && hoistingMayRemoveInstancePutToUninitializedThis(code);
  }

  private boolean hoistingMayRemoveInstancePutToUninitializedThis(IRCode code) {
    if (!code.metadata().mayHaveInstancePut()) {
      return false;
    }
    Value thisValue = code.getThis();
    WorkList<BasicBlock> worklist = WorkList.newIdentityWorkList();
    for (InvokeDirect invoke : getOrComputeSideEffectFreeConstructorCalls(code)) {
      // Check if any of the previous instructions in the current block has an instance-put that
      // assigns a field on `this`.
      if (IterableUtils.anyBefore(
          invoke.getBlock().getInstructions(),
          instruction -> isInstancePutToUninitializedThis(instruction, thisValue),
          instruction -> instruction == invoke)) {
        return true;
      }
      // Otherwise check if any of the (transitive) predecessor blocks has an instance-put that
      // assigns a field on `this`.
      worklist.addIfNotSeen(invoke.getBlock().getPredecessors());
    }
    return worklist
        .run(
            block -> {
              if (Iterables.any(
                  block.getInstructions(),
                  instruction -> isInstancePutToUninitializedThis(instruction, thisValue))) {
                return TraversalContinuation.doBreak();
              }
              worklist.addIfNotSeen(block.getPredecessors());
              return TraversalContinuation.doContinue();
            })
        .shouldBreak();
  }

  private List<InvokeDirect> getOrComputeSideEffectFreeConstructorCalls(IRCode code) {
    if (sideEffectFreeConstructorCalls == null) {
      sideEffectFreeConstructorCalls = computeSideEffectFreeConstructorCalls(code);
    }
    return sideEffectFreeConstructorCalls;
  }

  private List<InvokeDirect> computeSideEffectFreeConstructorCalls(IRCode code) {
    Value thisValue = code.getThis();
    return ListUtils.filter(
        thisValue.uniqueUsers(),
        instruction -> {
          if (!instruction.isInvokeConstructor(dexItemFactory)) {
            return false;
          }
          InvokeDirect invoke = instruction.asInvokeDirect();
          if (invoke.getReceiver() != thisValue) {
            return false;
          }
          DexClassAndMethod target = invoke.lookupSingleTarget(appView, code.context());
          return target != null
              && !target.getOptimizationInfo().mayHaveSideEffects(invoke, options);
        });
  }

  private static boolean isInstancePutToUninitializedThis(
      Instruction instruction, Value thisValue) {
    return instruction.isInstancePut() && instruction.asInstancePut().object() == thisValue;
  }
}
