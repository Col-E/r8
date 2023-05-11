// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class LinearFlowInstructionListIterator implements InstructionListIterator {

  private final IRCode code;

  private BasicBlock currentBlock;
  private InstructionListIterator currentBlockIterator;
  private Set<BasicBlock> seenBlocks = Sets.newIdentityHashSet();

  public LinearFlowInstructionListIterator(IRCode code, BasicBlock block) {
    this(code, block, 0);
  }

  public LinearFlowInstructionListIterator(IRCode code, BasicBlock block, int index) {
    this.code = code;
    this.currentBlock = block;
    this.currentBlockIterator = block.listIterator(code, index);
    // If index is pointing after the last instruction, and it is a goto with a linear edge,
    // we have to move the pointer. This is achieved by calling previous and next.
    seenBlocks.add(block);
    if (index > 0) {
      this.previous();
      this.next();
    }
  }

  public boolean hasVisitedBlock(BasicBlock basicBlock) {
    return seenBlocks.contains(basicBlock);
  }

  public Set<BasicBlock> getSeenBlocks() {
    return seenBlocks;
  }

  @Override
  public void replaceCurrentInstruction(Instruction newInstruction, Set<Value> affectedValues) {
    currentBlockIterator.replaceCurrentInstruction(newInstruction, affectedValues);
  }

  @Override
  public Value insertConstNumberInstruction(
      IRCode code, InternalOptions options, long value, TypeElement type) {
    return currentBlockIterator.insertConstNumberInstruction(code, options, value, type);
  }

  @Override
  public Value insertConstStringInstruction(AppView<?> appView, IRCode code, DexString value) {
    return currentBlockIterator.insertConstStringInstruction(appView, code, value);
  }

  @Override
  public InvokeMethod insertNullCheckInstruction(
      AppView<?> appView,
      IRCode code,
      BasicBlockIterator blockIterator,
      Value value,
      Position position) {
    return currentBlockIterator.insertNullCheckInstruction(
        appView, code, blockIterator, value, position);
  }

  @Override
  public boolean replaceCurrentInstructionByNullCheckIfPossible(
      AppView<?> appView, ProgramMethod context) {
    return currentBlockIterator.replaceCurrentInstructionByNullCheckIfPossible(appView, context);
  }

  @Override
  public boolean removeOrReplaceCurrentInstructionByInitClassIfPossible(
      AppView<?> appView, IRCode code, DexType type, Consumer<InitClass> consumer) {
    return currentBlockIterator.removeOrReplaceCurrentInstructionByInitClassIfPossible(
        appView, code, type, consumer);
  }

  @Override
  public void replaceCurrentInstructionWithConstClass(
      AppView<?> appView, IRCode code, DexType type, DebugLocalInfo localInfo) {
    currentBlockIterator.replaceCurrentInstructionWithConstClass(appView, code, type, localInfo);
  }

  @Override
  public void replaceCurrentInstructionWithConstInt(IRCode code, int value) {
    currentBlockIterator.replaceCurrentInstructionWithConstInt(code, value);
  }

  @Override
  public void replaceCurrentInstructionWithConstString(
      AppView<?> appView, IRCode code, DexString value) {
    currentBlockIterator.replaceCurrentInstructionWithConstString(appView, code, value);
  }

  @Override
  public void replaceCurrentInstructionWithNullCheck(AppView<?> appView, Value object) {
    currentBlockIterator.replaceCurrentInstructionWithNullCheck(appView, object);
  }

  @Override
  public void replaceCurrentInstructionWithStaticGet(
      AppView<?> appView, IRCode code, DexField field, Set<Value> affectedValues) {
    currentBlockIterator.replaceCurrentInstructionWithStaticGet(
        appView, code, field, affectedValues);
  }

  @Override
  public void replaceCurrentInstructionWithThrow(
      AppView<?> appView,
      IRCode code,
      BasicBlockIterator blockIterator,
      Value exceptionValue,
      Set<BasicBlock> blocksToRemove,
      Set<Value> affectedValues) {
    currentBlockIterator.replaceCurrentInstructionWithThrow(
        appView, code, blockIterator, exceptionValue, blocksToRemove, affectedValues);
  }

  @Override
  public void replaceCurrentInstructionWithThrowNull(
      AppView<?> appView,
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      Set<BasicBlock> blocksToRemove,
      Set<Value> affectedValues) {
    currentBlockIterator.replaceCurrentInstructionWithThrowNull(
        appView, code, blockIterator, blocksToRemove, affectedValues);
  }

  @Override
  public BasicBlock split(
      IRCode code, ListIterator<BasicBlock> blockIterator, boolean keepCatchHandlers) {
    return currentBlockIterator.split(code, blockIterator, keepCatchHandlers);
  }

  @Override
  public BasicBlock split(IRCode code, int instructions, ListIterator<BasicBlock> blockIterator) {
    return currentBlockIterator.split(code, instructions, blockIterator);
  }

  @Override
  public BasicBlock splitCopyCatchHandlers(
      IRCode code,
      BasicBlockIterator blockIterator,
      InternalOptions options,
      UnaryOperator<BasicBlock> repositioningBlock) {
    return currentBlockIterator.splitCopyCatchHandlers(
        code, blockIterator, options, repositioningBlock);
  }

  @Override
  public BasicBlock inlineInvoke(
      AppView<?> appView,
      IRCode code,
      IRCode inlinee,
      ListIterator<BasicBlock> blockIterator,
      Set<BasicBlock> blocksToRemove,
      DexProgramClass downcast) {
    return currentBlockIterator.inlineInvoke(
        appView, code, inlinee, blockIterator, blocksToRemove, downcast);
  }

  @Override
  public void add(Instruction instruction) {
    currentBlockIterator.add(instruction);
  }

  @Override
  public BasicBlock addThrowingInstructionToPossiblyThrowingBlock(
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      Instruction instruction,
      InternalOptions options) {
    return currentBlockIterator.addThrowingInstructionToPossiblyThrowingBlock(
        code, blockIterator, instruction, options);
  }

  @Override
  public void removeOrReplaceByDebugLocalRead() {
    currentBlockIterator.removeOrReplaceByDebugLocalRead();
  }

  private boolean isLinearEdge(BasicBlock pred, BasicBlock succ) {
    assert pred.getSuccessors().contains(succ);
    assert succ.getPredecessors().contains(pred);
    Goto exit = pred.exit().asGoto();
    return exit != null && exit.getTarget() == succ && succ.getPredecessors().size() == 1;
  }

  @Override
  public boolean hasNext() {
    return currentBlockIterator.hasNext();
  }

  @Override
  public Instruction next() {
    Instruction current = currentBlockIterator.next();
    if (!current.isGoto()) {
      return current;
    }
    BasicBlock target = current.asGoto().getTarget();
    if (!isLinearEdge(currentBlock, target)) {
      return current;
    }
    while (target.isTrivialGoto()) {
      BasicBlock candidate = target.exit().asGoto().getTarget();
      if (!isLinearEdge(target, candidate)) {
        break;
      }
      seenBlocks.add(target);
      target = candidate;
    }
    currentBlock = target;
    seenBlocks.add(target);
    currentBlockIterator = currentBlock.listIterator(code);
    return currentBlockIterator.next();
  }

  private BasicBlock getBeginningOfTrivialLinearGotoChain(BasicBlock block) {
    if (block.getPredecessors().size() != 1
        || !isLinearEdge(block.getPredecessors().get(0), block)) {
      return null;
    }
    BasicBlock target = block.getPredecessors().get(0);
    while (target.getPredecessors().size() == 1
        && isLinearEdge(target.getPredecessors().get(0), target)
        && target.isTrivialGoto()) {
      target = target.getPredecessors().get(0);
    }
    return target.isTrivialGoto() ? null : target;
  }

  @Override
  public boolean hasPrevious() {
    if (currentBlockIterator.hasPrevious()) {
      return true;
    }
    return getBeginningOfTrivialLinearGotoChain(currentBlock) != null;
  }

  @Override
  public Instruction previous() {
    if (currentBlockIterator.hasPrevious()) {
      return currentBlockIterator.previous();
    }
    BasicBlock target = getBeginningOfTrivialLinearGotoChain(currentBlock);
    if (target == null) {
      // No existing linear predecessor. Force an error by calling previous.
      return currentBlockIterator.previous();
    }
    currentBlock = target;
    seenBlocks.add(target);
    currentBlockIterator = currentBlock.listIterator(code, currentBlock.getInstructions().size());
    // Iterate over the jump.
    currentBlockIterator.previous();
    return currentBlockIterator.previous();
  }

  @Override
  public int nextIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int previousIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void remove() {
    currentBlockIterator.remove();
  }

  @Override
  public void set(Instruction instruction) {
    currentBlockIterator.set(instruction);
  }

  @Override
  public void set(Collection<Instruction> instructions) {
    currentBlockIterator.set(instructions);
  }
}
