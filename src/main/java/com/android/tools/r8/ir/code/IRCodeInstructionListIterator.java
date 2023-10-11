// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Collection;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class IRCodeInstructionListIterator implements InstructionListIterator {

  private final ListIterator<BasicBlock> blockIterator;
  private InstructionListIterator instructionIterator;

  private final IRCode code;

  public IRCodeInstructionListIterator(IRCode code) {
    this.blockIterator = code.listIterator();
    this.code = code;
    this.instructionIterator = blockIterator.next().listIterator(code);
  }

  @Override
  public Value insertConstNumberInstruction(
      IRCode code, InternalOptions options, long value, TypeElement type) {
    return instructionIterator.insertConstNumberInstruction(code, options, value, type);
  }

  @Override
  public Value insertConstStringInstruction(AppView<?> appView, IRCode code, DexString value) {
    return instructionIterator.insertConstStringInstruction(appView, code, value);
  }

  @Override
  public InvokeMethod insertNullCheckInstruction(
      AppView<?> appView,
      IRCode code,
      BasicBlockIterator blockIterator,
      Value value,
      Position position) {
    return instructionIterator.insertNullCheckInstruction(
        appView, code, blockIterator, value, position);
  }

  @Override
  public boolean replaceCurrentInstructionByNullCheckIfPossible(
      AppView<?> appView, ProgramMethod context) {
    return instructionIterator.replaceCurrentInstructionByNullCheckIfPossible(appView, context);
  }

  @Override
  public boolean removeOrReplaceCurrentInstructionByInitClassIfPossible(
      AppView<?> appView, IRCode code, DexType type, Consumer<InitClass> consumer) {
    return instructionIterator.removeOrReplaceCurrentInstructionByInitClassIfPossible(
        appView, code, type, consumer);
  }

  @Override
  public void replaceCurrentInstructionWithConstClass(
      AppView<?> appView, IRCode code, DexType type, DebugLocalInfo localInfo) {
    instructionIterator.replaceCurrentInstructionWithConstClass(appView, code, type, localInfo);
  }

  @Override
  public void replaceCurrentInstructionWithConstInt(IRCode code, int value) {
    instructionIterator.replaceCurrentInstructionWithConstInt(code, value);
  }

  @Override
  public void replaceCurrentInstructionWithConstString(
      AppView<?> appView, IRCode code, DexString value) {
    instructionIterator.replaceCurrentInstructionWithConstString(appView, code, value);
  }

  @Override
  public void replaceCurrentInstructionWithNullCheck(AppView<?> appView, Value object) {
    instructionIterator.replaceCurrentInstructionWithNullCheck(appView, object);
  }

  @Override
  public void replaceCurrentInstructionWithStaticGet(
      AppView<?> appView, IRCode code, DexField field, Set<Value> affectedValues) {
    instructionIterator.replaceCurrentInstructionWithStaticGet(
        appView, code, field, affectedValues);
  }

  @Override
  public void removeInstructionIgnoreOutValue() {
    instructionIterator.removeInstructionIgnoreOutValue();
  }

  @Override
  public void replaceCurrentInstructionWithThrow(
      AppView<?> appView,
      IRCode code,
      BasicBlockIterator blockIterator,
      Value exceptionValue,
      Set<BasicBlock> blocksToRemove,
      Set<Value> affectedValues) {
    throw new Unimplemented();
  }

  @Override
  public void replaceCurrentInstructionWithThrowNull(
      AppView<?> appView,
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      Set<BasicBlock> blocksToRemove,
      Set<Value> affectedValues) {
    throw new Unimplemented();
  }

  @Override
  public BasicBlock split(
      IRCode code, ListIterator<BasicBlock> blockIterator, boolean keepCatchHandlers) {
    throw new Unimplemented();
  }

  @Override
  public BasicBlock split(IRCode code, int instructions, ListIterator<BasicBlock> blockIterator) {
    throw new Unimplemented();
  }

  @Override
  public BasicBlock splitCopyCatchHandlers(
      IRCode code,
      BasicBlockIterator blockIterator,
      InternalOptions options,
      UnaryOperator<BasicBlock> repositioningBlock) {
    throw new Unimplemented();
  }

  @Override
  public BasicBlock inlineInvoke(
      AppView<?> appView,
      IRCode code,
      IRCode inlinee,
      ListIterator<BasicBlock> blockIterator,
      Set<BasicBlock> blocksToRemove,
      DexProgramClass downcast) {
    throw new Unimplemented();
  }

  @Override
  public boolean hasNext() {
    return instructionIterator.hasNext() || blockIterator.hasNext();
  }

  @Override
  public Instruction next() {
    if (instructionIterator.hasNext()) {
      return instructionIterator.next();
    }
    if (!blockIterator.hasNext()) {
      throw new NoSuchElementException();
    }
    instructionIterator = blockIterator.next().listIterator(code);
    assert instructionIterator.hasNext();
    return instructionIterator.next();
  }

  @Override
  public boolean hasPrevious() {
    return instructionIterator.hasPrevious() || blockIterator.hasPrevious();
  }

  @Override
  public Instruction previous() {
    if (instructionIterator.hasPrevious()) {
      return instructionIterator.previous();
    }
    if (!blockIterator.hasPrevious()) {
      throw new NoSuchElementException();
    }
    BasicBlock block = blockIterator.previous();
    instructionIterator = block.listIterator(code, block.getInstructions().size());
    assert instructionIterator.hasPrevious();
    return instructionIterator.previous();
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
  public void add(Instruction instruction) {
    instructionIterator.add(instruction);
  }

  @Override
  public InstructionListIterator addPossiblyThrowingInstructionsToPossiblyThrowingBlock(
      IRCode code,
      BasicBlockIterator blockIterator,
      Instruction[] instructions,
      InternalOptions options) {
    return instructionIterator.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(
        code, blockIterator, instructions, options);
  }

  @Override
  public BasicBlock addThrowingInstructionToPossiblyThrowingBlock(
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      Instruction instruction,
      InternalOptions options) {
    return instructionIterator.addThrowingInstructionToPossiblyThrowingBlock(
        code, blockIterator, instruction, options);
  }

  @Override
  public void remove() {
    instructionIterator.remove();
  }

  @Override
  public void set(Instruction instruction) {
    instructionIterator.set(instruction);
  }

  @Override
  public void set(Collection<Instruction> instructions) {
    instructionIterator.set(instructions);
  }

  @Override
  public void replaceCurrentInstruction(Instruction newInstruction, Set<Value> affectedValues) {
    instructionIterator.replaceCurrentInstruction(newInstruction, affectedValues);
  }

  @Override
  public void removeOrReplaceByDebugLocalRead() {
    instructionIterator.removeOrReplaceByDebugLocalRead();
  }

  @Override
  public boolean hasInsertionPosition() {
    return instructionIterator.hasInsertionPosition();
  }

  @Override
  public void setInsertionPosition(Position position) {
    instructionIterator.setInsertionPosition(position);
  }

  @Override
  public void unsetInsertionPosition() {
    instructionIterator.unsetInsertionPosition();
  }
}
