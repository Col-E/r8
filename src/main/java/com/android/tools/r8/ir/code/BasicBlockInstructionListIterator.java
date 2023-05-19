// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.android.tools.r8.ir.code.DominatorTree.Assumption.MAY_HAVE_UNREACHABLE_BLOCKS;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Phi.RegisterReadType;
import com.android.tools.r8.ir.optimize.NestUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IteratorUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class BasicBlockInstructionListIterator implements InstructionListIterator {

  protected final BasicBlock block;
  protected final ListIterator<Instruction> listIterator;
  protected Instruction current;
  protected Position position = null;

  private final IRMetadata metadata;

  BasicBlockInstructionListIterator(IRMetadata metadata, BasicBlock block) {
    this.block = block;
    this.listIterator = block.getInstructions().listIterator();
    this.metadata = metadata;
  }

  BasicBlockInstructionListIterator(IRMetadata metadata, BasicBlock block, int index) {
    this.block = block;
    this.listIterator = block.getInstructions().listIterator(index);
    this.metadata = metadata;
  }

  BasicBlockInstructionListIterator(
      IRMetadata metadata, BasicBlock block, Instruction instruction) {
    this(metadata, block);
    nextUntil(x -> x == instruction);
  }

  @Override
  public boolean hasNext() {
    return listIterator.hasNext();
  }

  @Override
  public Instruction next() {
    current = listIterator.next();
    return current;
  }

  @Override
  public int nextIndex() {
    return listIterator.nextIndex();
  }

  @Override
  public boolean hasPrevious() {
    return listIterator.hasPrevious();
  }

  @Override
  public Instruction previous() {
    current = listIterator.previous();
    return current;
  }

  @Override
  public int previousIndex() {
    return listIterator.previousIndex();
  }

  @Override
  public boolean hasInsertionPosition() {
    return position != null;
  }

  @Override
  public void setInsertionPosition(Position position) {
    this.position = position;
  }

  @Override
  public void unsetInsertionPosition() {
    this.position = null;
  }

  /**
   * Adds an instruction to the block. The instruction will be added just before the current cursor
   * position.
   *
   * <p>The instruction will be assigned to the block it is added to.
   *
   * @param instruction The instruction to add.
   */
  @Override
  public void add(Instruction instruction) {
    instruction.setBlock(block);
    assert instruction.getBlock() == block;
    if (position != null && !instruction.hasPosition()) {
      instruction.setPosition(position);
    }
    listIterator.add(instruction);
    metadata.record(instruction);
  }

  @Override
  public BasicBlock addThrowingInstructionToPossiblyThrowingBlock(
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      Instruction instruction,
      InternalOptions options) {
    if (block.hasCatchHandlers()) {
      BasicBlock splitBlock = split(code, blockIterator, false);
      splitBlock.listIterator(code).add(instruction);
      assert !block.hasCatchHandlers();
      assert splitBlock.hasCatchHandlers();
      block.copyCatchHandlers(code, blockIterator, splitBlock, options);
      if (blockIterator != null) {
        while (IteratorUtils.peekPrevious(blockIterator) != splitBlock) {
          blockIterator.previous();
        }
      }
      return splitBlock;
    } else {
      add(instruction);
      return null;
    }
  }

  /**
   * Replaces the last instruction returned by {@link #next} or {@link #previous} with the specified
   * instruction.
   *
   * <p>The instruction will be assigned to the block it is added to.
   *
   * @param instruction The instruction to replace with.
   */
  @Override
  public void set(Instruction instruction) {
    instruction.setBlock(block);
    assert instruction.getBlock() == block;
    listIterator.set(instruction);
    metadata.record(instruction);
  }

  @Override
  public void set(Collection<Instruction> instructions) {
    for (Instruction instruction : instructions) {
      set(instruction);
      next();
    }
  }

  /**
   * Remove the current instruction (aka the {@link Instruction} returned by the previous call to
   * {@link #next}.
   *
   * <p>The current instruction will be completely detached from the instruction stream with uses of
   * its in-values removed.
   *
   * <p>If the current instruction produces an out-value this out value must not have any users.
   */
  @Override
  public void remove() {
    if (current == null) {
      throw new IllegalStateException();
    }
    assert current.outValue() == null || !current.outValue().isUsed();
    assert current.getDebugValues().isEmpty();
    for (int i = 0; i < current.inValues().size(); i++) {
      Value value = current.inValues().get(i);
      value.removeUser(current);
    }
    // These needs to stay to ensure that an optimization incorrectly not taking debug info into
    // account still produces valid code when run without enabled assertions.
    for (Value value : current.getDebugValues()) {
      value.removeDebugUser(current);
    }
    if (current.getLocalInfo() != null) {
      for (Instruction user : current.outValue().debugUsers()) {
        user.removeDebugValue(current.outValue());
      }
    }
    listIterator.remove();
    current = null;
  }

  @Override
  public void removeInstructionIgnoreOutValue() {
    if (current == null) {
      throw new IllegalStateException();
    }
    listIterator.remove();
    current = null;
  }

  @Override
  public void removeOrReplaceByDebugLocalRead() {
    if (current == null) {
      throw new IllegalStateException();
    }
    if (current.getDebugValues().isEmpty()) {
      remove();
    } else {
      replaceCurrentInstruction(DebugLocalRead.INSTANCE);
    }
  }

  @Override
  public void replaceCurrentInstruction(Instruction newInstruction, Set<Value> affectedValues) {
    if (current == null) {
      throw new IllegalStateException();
    }
    for (Value value : current.inValues()) {
      value.removeUser(current);
    }
    if (current.hasUsedOutValue()) {
      assert newInstruction.outValue() != null;
      if (affectedValues != null && newInstruction.getOutType() != current.getOutType()) {
        current.outValue().addAffectedValuesTo(affectedValues);
      }
      current.outValue().replaceUsers(newInstruction.outValue());
    }
    current.moveDebugValues(newInstruction);
    newInstruction.setBlock(block);
    if (!newInstruction.hasPosition()) {
      newInstruction.setPosition(current.getPosition());
    }
    listIterator.remove();
    listIterator.add(newInstruction);
    current.clearBlock();
    metadata.record(newInstruction);
    current = newInstruction;
  }

  @Override
  public Value insertConstNumberInstruction(
      IRCode code, InternalOptions options, long value, TypeElement type) {
    ConstNumber constNumberInstruction = code.createNumberConstant(value, type);
    // Note that we only keep position info for throwing instructions in release mode.
    if (!hasInsertionPosition()) {
      Position position;
      if (options.debug) {
        position = current != null ? current.getPosition() : block.getPosition();
      } else {
        position = Position.none();
      }
      constNumberInstruction.setPosition(position);
    }
    add(constNumberInstruction);
    return constNumberInstruction.outValue();
  }

  @Override
  public Value insertConstStringInstruction(AppView<?> appView, IRCode code, DexString value) {
    ConstString constStringInstruction = code.createStringConstant(appView, value);
    // Note that we only keep position info for throwing instructions in release mode.
    constStringInstruction.setPosition(
        appView.options().debug ? current.getPosition() : Position.none());
    add(constStringInstruction);
    return constStringInstruction.outValue();
  }

  @Override
  public InvokeMethod insertNullCheckInstruction(
      AppView<?> appView,
      IRCode code,
      BasicBlockIterator blockIterator,
      Value value,
      Position position) {
    InternalOptions options = appView.options();
    DexMethod getClassMethod = appView.dexItemFactory().objectMembers.getClass;
    InvokeMethod invoke =
        InvokeVirtual.builder()
            .setMethod(getClassMethod)
            .setSingleArgument(value)
            .setPosition(position)
            .build();
    add(invoke);
    if (block.hasCatchHandlers()) {
      splitCopyCatchHandlers(code, blockIterator, options);
    }
    return invoke;
  }

  @Override
  public boolean replaceCurrentInstructionByNullCheckIfPossible(
      AppView<?> appView, ProgramMethod context) {
    Instruction toBeReplaced = current;
    assert toBeReplaced != null;
    assert toBeReplaced.isInstanceFieldInstruction() || toBeReplaced.isInvokeMethodWithReceiver();
    if (toBeReplaced.hasUsedOutValue()) {
      return false;
    }
    if (toBeReplaced.isInvokeDirect()) {
      DexItemFactory dexItemFactory = appView.dexItemFactory();
      DexMethod invokedMethod = toBeReplaced.asInvokeDirect().getInvokedMethod();
      if (invokedMethod.isInstanceInitializer(dexItemFactory)
          || invokedMethod.mustBeInlinedIntoInstanceInitializer(appView)) {
        return false;
      }
    }
    if (toBeReplaced.instructionMayHaveSideEffects(
        appView, context, Instruction.SideEffectAssumption.RECEIVER_NOT_NULL)) {
      return false;
    }
    Value receiver =
        toBeReplaced.isInstanceFieldInstruction()
            ? toBeReplaced.asInstanceFieldInstruction().object()
            : toBeReplaced.asInvokeMethodWithReceiver().getReceiver();
    if (receiver.isNeverNull()) {
      removeOrReplaceByDebugLocalRead();
      return true;
    }
    replaceCurrentInstructionWithNullCheck(appView, receiver);
    return true;
  }

  @Override
  public boolean removeOrReplaceCurrentInstructionByInitClassIfPossible(
      AppView<?> appView, IRCode code, DexType type, Consumer<InitClass> consumer) {
    Instruction toBeReplaced = current;
    assert toBeReplaced != null;
    assert toBeReplaced.isStaticFieldInstruction() || toBeReplaced.isInvokeStatic();
    if (toBeReplaced.hasUsedOutValue()) {
      return false;
    }
    ProgramMethod context = code.context();
    if (!toBeReplaced.instructionMayHaveSideEffects(appView, context)) {
      removeOrReplaceByDebugLocalRead();
      return true;
    }
    if (toBeReplaced.instructionMayHaveSideEffects(
        appView, context, Instruction.SideEffectAssumption.CLASS_ALREADY_INITIALIZED)) {
      return false;
    }
    if (!type.classInitializationMayHaveSideEffectsInContext(appView, context)) {
      removeOrReplaceByDebugLocalRead();
      return true;
    }
    if (!appView.canUseInitClass()) {
      return false;
    }
    DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
    if (clazz != null) {
      Value dest = code.createValue(TypeElement.getInt());
      InitClass initClass = new InitClass(dest, clazz.type);
      replaceCurrentInstruction(initClass);
      consumer.accept(initClass);
    }
    return true;
  }

  @Override
  public void replaceCurrentInstructionWithConstClass(
      AppView<?> appView, IRCode code, DexType type, DebugLocalInfo localInfo) {
    if (current == null) {
      throw new IllegalStateException();
    }

    TypeElement typeElement = TypeElement.classClassType(appView, definitelyNotNull());
    Value value = code.createValue(typeElement, localInfo);
    ConstClass constClass = new ConstClass(value, type);
    replaceCurrentInstruction(constClass);
  }

  @Override
  public void replaceCurrentInstructionWithConstInt(IRCode code, int value) {
    if (current == null) {
      throw new IllegalStateException();
    }

    assert !current.hasOutValue() || current.getOutType().isInt();

    // Replace the instruction by const-number.
    ConstNumber constNumber = code.createIntConstant(value, current.getLocalInfo());
    replaceCurrentInstruction(constNumber);
  }

  @Override
  public void replaceCurrentInstructionWithConstString(
      AppView<?> appView, IRCode code, DexString value) {
    if (current == null) {
      throw new IllegalStateException();
    }

    // Replace the instruction by const-string.
    ConstString constString = code.createStringConstant(appView, value, current.getLocalInfo());
    replaceCurrentInstruction(constString);
  }

  @Override
  public void replaceCurrentInstructionWithNullCheck(AppView<?> appView, Value object) {
    if (current == null) {
      throw new IllegalStateException();
    }

    assert current.hasUnusedOutValue();
    assert !block.hasCatchHandlers() || current.instructionTypeCanThrow();

    DexMethod getClassMethod = appView.dexItemFactory().objectMembers.getClass;
    replaceCurrentInstruction(
        InvokeVirtual.builder().setMethod(getClassMethod).setSingleArgument(object).build());
  }

  @Override
  public void replaceCurrentInstructionWithStaticGet(
      AppView<?> appView, IRCode code, DexField field, Set<Value> affectedValues) {
    if (current == null) {
      throw new IllegalStateException();
    }

    // Replace the instruction by static-get.
    TypeElement newType = TypeElement.fromDexType(field.type, maybeNull(), appView);
    TypeElement oldType = current.getOutType();
    Value value = code.createValue(newType, current.getLocalInfo());
    StaticGet staticGet = new StaticGet(value, field);
    replaceCurrentInstruction(staticGet);

    // Update affected values.
    if (value.hasAnyUsers() && !newType.equals(oldType)) {
      affectedValues.addAll(value.affectedValues());
    }
  }

  @Override
  public void replaceCurrentInstructionWithThrow(
      AppView<?> appView,
      IRCode code,
      BasicBlockIterator blockIterator,
      Value exceptionValue,
      Set<BasicBlock> blocksToRemove,
      Set<Value> affectedValues) {
    if (current == null) {
      throw new IllegalStateException();
    }

    Instruction toBeReplaced = current;
    InternalOptions options = appView.options();

    BasicBlock block = toBeReplaced.getBlock();
    assert !blocksToRemove.contains(block);
    assert affectedValues != null;

    // Split the block before the instruction that should be replaced by `throw exceptionValue`.
    previous();

    BasicBlock throwBlock;
    if (block.hasCatchHandlers() && !toBeReplaced.instructionTypeCanThrow()) {
      // We need to insert the throw instruction in a block of its own, so split the current block
      // into three blocks, where the intermediate block only contains a goto instruction.
      throwBlock = splitCopyCatchHandlers(code, blockIterator, options);
      throwBlock.listIterator(code).split(code, blockIterator, true);
    } else {
      splitCopyCatchHandlers(code, blockIterator, options);
      throwBlock = block;
    }

    // Unlink all blocks that are dominated by the unique normal successor of the throw block.
    blocksToRemove.addAll(
        throwBlock.unlink(
            throwBlock.getUniqueNormalSuccessor(),
            new DominatorTree(code, MAY_HAVE_UNREACHABLE_BLOCKS),
            affectedValues));

    InstructionListIterator throwBlockInstructionIterator;
    if (throwBlock == block) {
      throwBlockInstructionIterator = this;
      previous();
      next();
    } else {
      throwBlockInstructionIterator = throwBlock.listIterator(code, 1);
    }
    assert !throwBlockInstructionIterator.hasNext();

    // Replace the instruction by throw.
    Throw throwInstruction = new Throw(exceptionValue);
    if (hasInsertionPosition()) {
      throwInstruction.setPosition(position);
    } else if (toBeReplaced.getPosition().isSome()) {
      throwInstruction.setPosition(toBeReplaced.getPosition());
    } else {
      assert !toBeReplaced.instructionTypeCanThrow();
      throwInstruction.setPosition(Position.syntheticNone());
    }
    throwBlockInstructionIterator.replaceCurrentInstruction(throwInstruction);
  }

  @Override
  public void replaceCurrentInstructionWithThrowNull(
      AppView<?> appView,
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      Set<BasicBlock> blocksToRemove,
      Set<Value> affectedValues) {
    if (current == null) {
      throw new IllegalStateException();
    }

    Instruction toBeReplaced = current;

    BasicBlock block = toBeReplaced.getBlock();
    assert !blocksToRemove.contains(block);
    assert affectedValues != null;

    // Split the block before the instruction that should be replaced by `throw null`.
    previous();

    BasicBlock throwBlock;
    if (block.hasCatchHandlers() && !toBeReplaced.instructionTypeCanThrow()) {
      // We need to insert the throw instruction in a block of its own, so split the current block
      // into three blocks, where the intermediate block only contains a goto instruction.
      throwBlock = split(code, blockIterator, true);
      throwBlock.listIterator(code).split(code, blockIterator);
    } else {
      split(code, blockIterator, true);
      throwBlock = block;
    }

    // Position the instruction iterator before the goto instruction.
    assert !hasNext();
    previous();

    // Unlink all blocks that are dominated by the unique normal successor of the throw block.
    blocksToRemove.addAll(
        throwBlock.unlink(
            throwBlock.getUniqueNormalSuccessor(),
            new DominatorTree(code, MAY_HAVE_UNREACHABLE_BLOCKS),
            affectedValues));

    InstructionListIterator throwBlockInstructionIterator;
    if (throwBlock == block) {
      throwBlockInstructionIterator = this;
    } else {
      throwBlockInstructionIterator = throwBlock.listIterator(code);
      throwBlockInstructionIterator.setInsertionPosition(position);
    }

    // Insert constant null before the goto instruction.
    Value nullValue =
        throwBlockInstructionIterator.insertConstNullInstruction(code, appView.options());

    // Move past the inserted goto instruction.
    throwBlockInstructionIterator.next();
    assert !throwBlockInstructionIterator.hasNext();

    // Replace the instruction by throw.
    Throw throwInstruction = new Throw(nullValue);
    if (hasInsertionPosition()) {
      throwInstruction.setPosition(position);
    } else if (toBeReplaced.getPosition().isSome()) {
      throwInstruction.setPosition(toBeReplaced.getPosition());
    } else {
      // The instruction that is being removed cannot throw, and thus it must be unreachable as we
      // are replacing it by `throw null`, so we can safely use a none-position.
      assert !toBeReplaced.instructionTypeCanThrow();
      throwInstruction.setPosition(Position.syntheticNone());
    }
    throwBlockInstructionIterator.replaceCurrentInstruction(throwInstruction);

    if (block.hasCatchHandlers()) {
      if (block == throwBlock) {
        // Remove all catch handlers where the guard does not include NullPointerException if the
        // replaced instruction could throw.
        CatchHandlers<BasicBlock> catchHandlers = block.getCatchHandlers();
        catchHandlers.forEach(
            (guard, target) -> {
              if (blocksToRemove.contains(target)) {
                // Already removed previously. This may happen if two catch handlers have the same
                // target.
                return;
              }
              if (appView.isSubtype(appView.dexItemFactory().npeType, guard).isFalse()) {
                // TODO(christofferqa): Consider updating previous dominator tree instead of
                //   rebuilding it from scratch.
                DominatorTree dominatorTree = new DominatorTree(code, MAY_HAVE_UNREACHABLE_BLOCKS);
                blocksToRemove.addAll(block.unlink(target, dominatorTree, affectedValues));
              }
            });
      } else {
        // This is dead code and does not need to be guarded by catch handlers, except in the case
        // of locking, which should be statically verifiable. Currently we copy over the handlers of
        // the block in any case which is sound in all cases.
        throwBlock.copyCatchHandlers(code, blockIterator, block, appView.options());
      }
    }
  }

  @Override
  public BasicBlock split(
      IRCode code, ListIterator<BasicBlock> blocksIterator, boolean keepCatchHandlers) {
    List<BasicBlock> blocks = code.blocks;
    assert blocksIterator == null || IteratorUtils.peekPrevious(blocksIterator) == block;

    // Don't allow splitting after the last instruction.
    assert hasNext();

    // Get the position at which the block is being split.
    Position position = current != null ? current.getPosition() : block.getPosition();

    // Prepare the new block, placing the exception handlers on the block with the throwing
    // instruction.
    BasicBlock newBlock = block.createSplitBlock(code.getNextBlockNumber(), keepCatchHandlers);

    // Add a goto instruction.
    Goto newGoto = new Goto(block);
    listIterator.add(newGoto);
    newGoto.setPosition(position);

    // Move all remaining instructions to the new block.
    while (listIterator.hasNext()) {
      Instruction instruction = listIterator.next();
      newBlock.getInstructions().addLast(instruction);
      instruction.setBlock(newBlock);
      listIterator.remove();
    }

    // Insert the new block in the block list right after the current block.
    if (blocksIterator == null) {
      blocks.add(blocks.indexOf(block) + 1, newBlock);
    } else {
      blocksIterator.add(newBlock);
      // Ensure that calling remove() will remove the block just added.
      blocksIterator.previous();
      blocksIterator.next();
    }

    return newBlock;
  }

  @Override
  public BasicBlock split(IRCode code, int instructions, ListIterator<BasicBlock> blocksIterator) {
    // Split at the current cursor position.
    BasicBlock newBlock = split(code, blocksIterator);
    assert blocksIterator == null || IteratorUtils.peekPrevious(blocksIterator) == newBlock;
    // Skip the requested number of instructions and split again.
    InstructionListIterator iterator = newBlock.listIterator(code);
    for (int i = 0; i < instructions; i++) {
      iterator.next();
    }
    iterator.split(code, blocksIterator);
    // Return the first split block.
    return newBlock;
  }

  @Override
  public BasicBlock splitCopyCatchHandlers(
      IRCode code,
      BasicBlockIterator blockIterator,
      InternalOptions options,
      UnaryOperator<BasicBlock> repositioningBlock) {
    BasicBlock splitBlock = split(code, blockIterator, false);
    assert !block.hasCatchHandlers();
    if (splitBlock.hasCatchHandlers()) {
      block.copyCatchHandlers(code, blockIterator, splitBlock, options);
    }
    if (repositioningBlock != null) {
      blockIterator.positionAfterPreviousBlock(repositioningBlock.apply(splitBlock));
    }
    return splitBlock;
  }

  private boolean canThrow(IRCode code) {
    InstructionIterator iterator = code.instructionIterator();
    while (iterator.hasNext()) {
      boolean throwing = iterator.next().instructionTypeCanThrow();
      if (throwing) {
        return true;
      }
    }
    return false;
  }

  private void splitBlockAndCopyCatchHandlers(
      AppView<?> appView,
      IRCode code,
      BasicBlock invokeBlock,
      BasicBlock inlinedBlock,
      ListIterator<BasicBlock> blocksIterator) {
    // Iterate through the instructions in the inlined block and split into blocks with only
    // one throwing instruction in each block.
    // NOTE: This iterator is replaced in the loop below, so that the iteration continues in
    // the new block after the iterated block is split.
    InstructionListIterator instructionsIterator = inlinedBlock.listIterator(code);
    BasicBlock currentBlock = inlinedBlock;
    while (currentBlock != null && instructionsIterator.hasNext()) {
      assert !currentBlock.hasCatchHandlers();
      Instruction throwingInstruction =
          instructionsIterator.nextUntil(Instruction::instructionTypeCanThrow);
      BasicBlock nextBlock;
      if (throwingInstruction != null) {
        // If a throwing instruction was found split the block.
        if (instructionsIterator.hasNext()) {
          // TODO(sgjesse): No need to split if this is the last non-debug, non-jump
          // instruction in the block.
          nextBlock = instructionsIterator.split(code, blocksIterator);
          assert nextBlock.getPredecessors().size() == 1;
          assert currentBlock == nextBlock.getPredecessors().get(0);
          // Back up to before the split before inserting catch handlers.
          BasicBlock b = blocksIterator.previous();
          assert b == nextBlock;
        } else {
          nextBlock = null;
        }
        currentBlock.copyCatchHandlers(code, blocksIterator, invokeBlock, appView.options());
        if (nextBlock != null) {
          BasicBlock b = blocksIterator.next();
          assert b == nextBlock;
          // Switch iteration to the split block.
          instructionsIterator = nextBlock.listIterator(code);
        } else {
          instructionsIterator = null;
        }
        currentBlock = nextBlock;
      } else {
        assert !instructionsIterator.hasNext();
        instructionsIterator = null;
        currentBlock = null;
      }
    }
  }

  private void appendCatchHandlers(
      AppView<?> appView,
      IRCode code,
      BasicBlock invokeBlock,
      IRCode inlinee,
      ListIterator<BasicBlock> blocksIterator) {
    // Position right after the empty invoke block, by moving back through the newly added inlinee
    // blocks (they are now in the basic blocks list).
    for (int i = 0; i < inlinee.blocks.size(); i++) {
      blocksIterator.previous();
    }
    assert IteratorUtils.peekNext(blocksIterator) == inlinee.entryBlock();
    // Iterate through the inlined blocks.
    for (BasicBlock inlinedBlock : inlinee.blocks) {
      BasicBlock expected = blocksIterator.next();
      assert inlinedBlock == expected; // Iterators must be in sync.
      if (inlinedBlock.hasCatchHandlers()) {
        // The block already has catch handlers, so it has only one throwing instruction, and no
        // splitting is required.
        inlinedBlock.copyCatchHandlers(code, blocksIterator, invokeBlock, appView.options());
      } else {
        // The block does not have catch handlers, so it can have several throwing instructions.
        // Therefore the block must be split after each throwing instruction, and the catch
        // handlers must be added to each of these blocks.
        splitBlockAndCopyCatchHandlers(appView, code, invokeBlock, inlinedBlock, blocksIterator);
      }
    }
  }

  private static void removeArgumentInstruction(
      InstructionListIterator iterator, Value expectedArgument) {
    assert iterator.hasNext();
    Instruction instruction = iterator.next();
    assert instruction.isArgument();
    assert !instruction.outValue().isUsed();
    assert instruction.outValue() == expectedArgument;
    iterator.remove();
  }

  @Override
  public BasicBlock inlineInvoke(
      AppView<?> appView,
      IRCode code,
      IRCode inlinee,
      ListIterator<BasicBlock> blocksIterator,
      Set<BasicBlock> blocksToRemove,
      DexProgramClass downcast) {
    assert blocksToRemove != null;
    ProgramMethod callerContext = code.context();
    ProgramMethod calleeContext = inlinee.context();
    if (callerContext.getHolder() != calleeContext.getHolder()
        && calleeContext.getDefinition().isOnlyInlinedIntoNestMembers()) {
      // Should rewrite private calls to virtual calls.
      assert NestUtils.sameNest(
          callerContext.getHolderType(), calleeContext.getHolderType(), appView);
      NestUtils.rewriteNestCallsForInlining(inlinee, callerContext, appView);
    }
    boolean inlineeCanThrow = canThrow(inlinee);
    // Split the block with the invocation into three blocks, where the first block contains all
    // instructions before the invocation, the second block contains only the invocation, and the
    // third block contains all instructions that follow the invocation.
    BasicBlock invokeBlock = split(code, 1, blocksIterator);
    assert invokeBlock.getInstructions().size() == 2;
    assert invokeBlock.getInstructions().getFirst().isInvoke();

    Invoke invoke = invokeBlock.getInstructions().getFirst().asInvoke();
    BasicBlock invokePredecessor = invokeBlock.getPredecessors().get(0);
    BasicBlock invokeSuccessor = invokeBlock.getSuccessors().get(0);

    Set<Value> argumentUsers = Sets.newIdentityHashSet();

    // Map all argument values. The first one needs special handling if there is a downcast type.
    List<Value> arguments = inlinee.collectArguments();
    assert invoke.inValues().size() == arguments.size();

    BasicBlock entryBlock = inlinee.entryBlock();
    InstructionListIterator entryBlockIterator;

    int i = 0;
    assert downcast == null || arguments.get(0).isThis();
    if (downcast != null && arguments.get(0).isUsed()) {
      // TODO(b/120257211): Even if the receiver is used, we can avoid inserting a check-cast
      //  instruction if the program still type checks without the cast.
      Value receiver = invoke.inValues().get(0);
      TypeElement castTypeLattice =
          TypeElement.fromDexType(downcast.getType(), receiver.getType().nullability(), appView);
      SafeCheckCast castInstruction =
          new SafeCheckCast(code.createValue(castTypeLattice), receiver, downcast.getType());
      castInstruction.setPosition(invoke.getPosition());

      // Splice in the check cast operation.
      if (entryBlock.canThrow()) {
        // Since the cast-instruction may also fail we need to create a new block for the cast.
        //
        // Note that the downcast of the receiver is made at the call site, so we need to copy the
        // catch handlers from the invoke block to the block with the cast. This is already being
        // done when we copy the catch handlers of the invoke block (if any) to all the blocks in
        // the inlinee (by the call to appendCatchHandlers() later in this method), so we don't
        // need to do anything about that here.
        BasicBlock inlineEntry = entryBlock;
        entryBlock = entryBlock.listIterator(code).split(inlinee);
        entryBlockIterator = entryBlock.listIterator(code);
        // Insert cast instruction into the new block.
        inlineEntry.listIterator(code).add(castInstruction);
        castInstruction.setBlock(inlineEntry);
        assert castInstruction.getBlock().getInstructions().size() == 2;
      } else {
        castInstruction.setBlock(entryBlock);
        entryBlockIterator = entryBlock.listIterator(code);
        entryBlockIterator.add(castInstruction);
      }

      // Map the argument value that has been cast.
      Value argument = arguments.get(i);
      argumentUsers.addAll(argument.affectedValues());
      argument.replaceUsers(castInstruction.outValue);
      removeArgumentInstruction(entryBlockIterator, argument);
      i++;
    } else {
      entryBlockIterator = entryBlock.listIterator(code);
    }

    // Map the remaining argument values.
    for (; i < invoke.inValues().size(); i++) {
      // TODO(zerny): Support inlining in --debug mode.
      assert !arguments.get(i).hasLocalInfo();
      Value argument = arguments.get(i);
      argumentUsers.addAll(argument.affectedValues());
      argument.replaceUsers(invoke.inValues().get(i));
      removeArgumentInstruction(entryBlockIterator, argument);
    }

    assert entryBlock.getInstructions().stream().noneMatch(Instruction::isArgument);

    // Actual arguments are flown to the inlinee.
    new TypeAnalysis(appView).narrowing(argumentUsers);

    // The inline entry is the first block now the argument instructions are gone.
    BasicBlock inlineEntry = inlinee.entryBlock();

    BasicBlock inlineExit = null;
    List<BasicBlock> normalExits = inlinee.computeNormalExitBlocks();
    if (!normalExits.isEmpty()) {
      // Ensure and locate the single return instruction of the inlinee.
      InstructionListIterator inlineeIterator =
          ensureSingleReturnInstruction(appView, inlinee, normalExits);

      // Replace the invoke value with the return value if non-void.
      assert inlineeIterator.peekNext().isReturn();
      if (invoke.outValue() != null) {
        Set<Value> affectedValues = invoke.outValue().affectedValues();
        Return returnInstruction = inlineeIterator.peekNext().asReturn();
        invoke.outValue().replaceUsers(returnInstruction.returnValue());
        // The return type is flown to the original context.
        new TypeAnalysis(appView)
            .narrowing(
                Iterables.concat(
                    ImmutableList.of(returnInstruction.returnValue()), affectedValues));
      }

      // Split before return and unlink return.
      BasicBlock returnBlock = inlineeIterator.split(inlinee);
      inlineExit = returnBlock.unlinkSinglePredecessor();
      InstructionListIterator returnBlockIterator = returnBlock.listIterator(code);
      returnBlockIterator.next();
      returnBlockIterator.remove(); // This clears out the users from the return.
      assert !returnBlockIterator.hasNext();
      inlinee.blocks.remove(returnBlock);

      // Leaving the invoke block in the graph as an empty block. Still unlink its predecessor as
      // the exit block of the inlinee will become its new predecessor.
      invokeBlock.unlinkSinglePredecessor();
      InstructionListIterator invokeBlockIterator = invokeBlock.listIterator(code);
      invokeBlockIterator.next();
      invokeBlockIterator.remove();
      invokeSuccessor = invokeBlock;
      assert invokeBlock.getInstructions().getFirst().isGoto();
    }

    // Link the inlinee into the graph.
    invokePredecessor.link(inlineEntry);
    if (inlineExit != null) {
      inlineExit.link(invokeSuccessor);
    }

    // Position the block iterator cursor just after the invoke block.
    if (blocksIterator == null) {
      // If no block iterator was passed create one for the insertion of the inlinee blocks.
      blocksIterator = code.listIterator(code.blocks.indexOf(invokeBlock));
    } else {
      // If a block iterator was passed, back up to the block with the invoke instruction.
      blocksIterator.previous();
      blocksIterator.previous();
    }
    assert IteratorUtils.peekNext(blocksIterator) == invokeBlock;

    // Insert inlinee blocks into the IR code of the callee, before the invoke block.
    for (BasicBlock bb : inlinee.blocks) {
      bb.setNumber(code.getNextBlockNumber());
      blocksIterator.add(bb);
    }

    // If the invoke block had catch handlers copy those down to all inlined blocks.
    if (invokeBlock.hasCatchHandlers()) {
      appendCatchHandlers(appView, code, invokeBlock, inlinee, blocksIterator);
    }

    // If there are no normal exists, then unlink the invoke block and all the blocks that it
    // dominates. This must be done after the catch handlers have been appended to the inlinee,
    // since the catch handlers are dominated by the inline block until then (meaning that the
    // catch handlers would otherwise be removed although they are not actually dead).
    if (normalExits.isEmpty()) {
      assert inlineeCanThrow;
      DominatorTree dominatorTree = new DominatorTree(code, MAY_HAVE_UNREACHABLE_BLOCKS);
      Set<Value> affectedValues = Sets.newIdentityHashSet();
      blocksToRemove.addAll(invokePredecessor.unlink(invokeBlock, dominatorTree, affectedValues));
      new TypeAnalysis(appView).narrowing(affectedValues);
    }

    // Position the iterator after the invoke block.
    blocksIterator.next();
    assert IteratorUtils.peekPrevious(blocksIterator) == invokeBlock;

    // Check that the successor of the invoke block is still to be processed,
    final BasicBlock finalInvokeSuccessor = invokeSuccessor;
    assert invokeSuccessor == invokeBlock
        || IteratorUtils.anyRemainingMatch(
            blocksIterator, remaining -> remaining == finalInvokeSuccessor);

    code.metadata().merge(inlinee.metadata());
    return invokeSuccessor;
  }

  private InstructionListIterator ensureSingleReturnInstruction(
      AppView<?> appView, IRCode code, List<BasicBlock> normalExits) {
    if (normalExits.size() == 1) {
      InstructionListIterator it = normalExits.get(0).listIterator(code);
      it.nextUntil(Instruction::isReturn);
      it.previous();
      return it;
    }
    BasicBlock newExitBlock = new BasicBlock();
    newExitBlock.setNumber(code.getNextBlockNumber());
    Return newReturn;
    if (normalExits.get(0).exit().asReturn().isReturnVoid()) {
      newReturn = new Return();
    } else {
      boolean same = true;
      List<Value> operands = new ArrayList<>(normalExits.size());
      for (BasicBlock exitBlock : normalExits) {
        Return exit = exitBlock.exit().asReturn();
        Value retValue = exit.returnValue();
        operands.add(retValue);
        same = same && retValue == operands.get(0);
      }
      Value value;
      if (same) {
        value = operands.get(0);
      } else {
        Phi phi =
            new Phi(
                code.valueNumberGenerator.next(),
                newExitBlock,
                TypeElement.getBottom(),
                null,
                RegisterReadType.NORMAL);
        phi.addOperands(operands);
        new TypeAnalysis(appView).widening(ImmutableSet.of(phi));
        value = phi;
      }
      newReturn = new Return(value);
    }
    // The newly constructed return will be eliminated as part of inlining so we set position none.
    newReturn.setPosition(Position.none());
    newExitBlock.add(newReturn, metadata);
    for (BasicBlock exitBlock : normalExits) {
      InstructionListIterator it = exitBlock.listIterator(code, exitBlock.getInstructions().size());
      Instruction oldExit = it.previous();
      assert oldExit.isReturn();
      it.replaceCurrentInstruction(new Goto());
      exitBlock.link(newExitBlock);
    }
    newExitBlock.close(null);
    code.blocks.add(newExitBlock);
    return newExitBlock.listIterator(code);
  }
}
