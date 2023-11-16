// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.ir.code.Opcodes.CONST_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.DEX_ITEM_BASED_CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.Binop;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InstructionOrPhi;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.ConstantCanonicalizer;
import com.android.tools.r8.utils.LazyBox;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class DexConstantOptimizer extends CodeRewriterPass<AppInfo> {

  // This constant was determined by experimentation.
  private static final int STOP_SHARED_CONSTANT_THRESHOLD = 50;

  private final ConstantCanonicalizer constantCanonicalizer;

  public DexConstantOptimizer(AppView<?> appView, ConstantCanonicalizer constantCanonicalizer) {
    super(appView);
    this.constantCanonicalizer = constantCanonicalizer;
  }

  @Override
  protected String getRewriterId() {
    return "DexConstantOptimizer";
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    useDedicatedConstantForLitInstruction(code);
    shortenLiveRanges(code, constantCanonicalizer);
    return CodeRewriterResult.NONE;
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return true;
  }

  /**
   * If an instruction is known to be a binop/lit8 or binop//lit16 instruction, update the
   * instruction to use its own constant that will be defined just before the instruction. This
   * transformation allows to decrease pressure on register allocation by defining the shortest
   * range of constant used by this kind of instruction. D8 knowns at build time that constant will
   * be encoded directly into the final Dex instruction.
   */
  private void useDedicatedConstantForLitInstruction(IRCode code) {
    if (!code.metadata().mayHaveArithmeticOrLogicalBinop()) {
      return;
    }

    for (BasicBlock block : code.blocks) {
      InstructionListIterator instructionIterator = block.listIterator(code);
      // Collect all the non constant in values for binop/lit8 or binop/lit16 instructions.
      Set<Value> binopsWithLit8OrLit16NonConstantValues = Sets.newIdentityHashSet();
      while (instructionIterator.hasNext()) {
        Instruction currentInstruction = instructionIterator.next();
        if (!isBinopWithLit8OrLit16(currentInstruction)) {
          continue;
        }
        Value value = binopWithLit8OrLit16NonConstant(currentInstruction.asBinop());
        assert value != null;
        binopsWithLit8OrLit16NonConstantValues.add(value);
      }
      if (binopsWithLit8OrLit16NonConstantValues.isEmpty()) {
        continue;
      }
      // Find last use in block of all the non constant in values for binop/lit8 or binop/lit16
      // instructions.
      Reference2IntMap<Value> lastUseOfBinopsWithLit8OrLit16NonConstantValues =
          new Reference2IntOpenHashMap<>();
      lastUseOfBinopsWithLit8OrLit16NonConstantValues.defaultReturnValue(-1);
      int currentInstructionNumber = block.getInstructions().size();
      while (instructionIterator.hasPrevious()) {
        Instruction currentInstruction = instructionIterator.previous();
        currentInstructionNumber--;
        for (Value value :
            Iterables.concat(currentInstruction.inValues(), currentInstruction.getDebugValues())) {
          if (!binopsWithLit8OrLit16NonConstantValues.contains(value)) {
            continue;
          }
          if (!lastUseOfBinopsWithLit8OrLit16NonConstantValues.containsKey(value)) {
            lastUseOfBinopsWithLit8OrLit16NonConstantValues.put(value, currentInstructionNumber);
          }
        }
      }
      // Do the transformation except if the binop can use the binop/2addr format.
      currentInstructionNumber--;
      assert currentInstructionNumber == -1;
      while (instructionIterator.hasNext()) {
        Instruction currentInstruction = instructionIterator.next();
        currentInstructionNumber++;
        if (!isBinopWithLit8OrLit16(currentInstruction)) {
          continue;
        }
        Binop binop = currentInstruction.asBinop();
        if (!canBe2AddrInstruction(
            binop, currentInstructionNumber, lastUseOfBinopsWithLit8OrLit16NonConstantValues)) {
          Value constValue = binopWithLit8OrLit16Constant(currentInstruction);
          if (constValue.numberOfAllUsers() > 1) {
            // No need to do the transformation if the const value is already used only one time.
            ConstNumber newConstant =
                ConstNumber.copyOf(code, constValue.definition.asConstNumber());
            newConstant.setPosition(currentInstruction.getPosition());
            newConstant.setBlock(currentInstruction.getBlock());
            currentInstruction.replaceValue(constValue, newConstant.outValue());
            constValue.removeUser(currentInstruction);
            instructionIterator.previous();
            instructionIterator.add(newConstant);
            instructionIterator.next();
          }
        }
      }
    }
  }

  // Check if a binop can be represented in the binop/lit8 or binop/lit16 form.
  private static boolean isBinopWithLit8OrLit16(Instruction instruction) {
    if (!instruction.isArithmeticBinop() && !instruction.isLogicalBinop()) {
      return false;
    }
    Binop binop = instruction.asBinop();
    // If one of the values does not need a register it is implicitly a binop/lit8 or binop/lit16.
    boolean result =
        !binop.needsValueInRegister(binop.leftValue())
            || !binop.needsValueInRegister(binop.rightValue());
    assert !result || binop.leftValue().isConstNumber() || binop.rightValue().isConstNumber();
    return result;
  }

  // Return the constant in-value of a binop/lit8 or binop/lit16 instruction.
  private static Value binopWithLit8OrLit16Constant(Instruction instruction) {
    assert isBinopWithLit8OrLit16(instruction);
    Binop binop = instruction.asBinop();
    if (binop.leftValue().isConstNumber()) {
      return binop.leftValue();
    } else if (binop.rightValue().isConstNumber()) {
      return binop.rightValue();
    } else {
      throw new Unreachable();
    }
  }

  // Return the non-constant in-value of a binop/lit8 or binop/lit16 instruction.
  private static Value binopWithLit8OrLit16NonConstant(Binop binop) {
    if (binop.leftValue().isConstNumber()) {
      return binop.rightValue();
    } else if (binop.rightValue().isConstNumber()) {
      return binop.leftValue();
    } else {
      throw new Unreachable();
    }
  }

  /**
   * Estimate if a binary operation can be a binop/2addr form or not. It can be a 2addr form when an
   * argument is no longer needed after the binary operation and can be overwritten. That is
   * definitely the case if there is no path between the binary operation and all other usages.
   */
  private static boolean canBe2AddrInstruction(
      Binop binop, int binopInstructionNumber, Reference2IntMap<Value> lastUseOfRelevantValue) {
    Value value = binopWithLit8OrLit16NonConstant(binop);
    assert value != null;
    int lastUseInstructionNumber = lastUseOfRelevantValue.getInt(value);
    // The binop instruction is a user, so there is always a last use in the block.
    assert lastUseInstructionNumber != -1;
    if (lastUseInstructionNumber > binopInstructionNumber) {
      return false;
    }

    Set<BasicBlock> noPathTo = Sets.newIdentityHashSet();
    BasicBlock binopBlock = binop.getBlock();
    Iterable<InstructionOrPhi> users =
        value.debugUsers() != null
            ? Iterables.concat(value.uniqueUsers(), value.debugUsers(), value.uniquePhiUsers())
            : Iterables.concat(value.uniqueUsers(), value.uniquePhiUsers());
    for (InstructionOrPhi user : users) {
      BasicBlock userBlock = user.getBlock();
      if (userBlock == binopBlock) {
        // All users in the current block are either before the binop instruction or the
        // binop instruction itself.
        continue;
      }
      if (noPathTo.contains(userBlock)) {
        continue;
      }
      if (binopBlock.hasPathTo(userBlock)) {
        return false;
      }
      noPathTo.add(userBlock);
    }

    return true;
  }

  private void shortenLiveRanges(IRCode code, ConstantCanonicalizer canonicalizer) {
    if (options.debug) {
      // Shorten live ranges seems to regress code size in debug mode.
      return;
    }
    if (options.testing.disableShortenLiveRanges) {
      return;
    }
    LazyBox<DominatorTree> dominatorTreeMemoization = new LazyBox<>(() -> new DominatorTree(code));
    Map<BasicBlock, LinkedHashMap<Value, Instruction>> addConstantInBlock = new IdentityHashMap<>();
    LinkedList<BasicBlock> blocks = code.blocks;
    for (BasicBlock block : blocks) {
      shortenLiveRangesInsideBlock(
          code, block, dominatorTreeMemoization, addConstantInBlock, canonicalizer::isConstant);
    }

    // Heuristic to decide if constant instructions are shared in dominator block
    // of usages or moved to the usages.

    // Process all blocks in stable order to avoid non-determinism of hash map iterator.
    BasicBlockIterator blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      Map<Value, Instruction> movedInstructions = addConstantInBlock.get(block);
      if (movedInstructions == null) {
        continue;
      }
      assert !movedInstructions.isEmpty();
      if (!block.isEntry() && movedInstructions.size() > STOP_SHARED_CONSTANT_THRESHOLD) {
        // If there are too many constant numbers in the same block they are copied rather than
        // shared unless used by a phi.
        movedInstructions
            .values()
            .removeIf(
                movedInstruction -> {
                  if (movedInstruction.outValue().hasPhiUsers()
                      || !movedInstruction.isConstNumber()) {
                    return false;
                  }
                  ConstNumber constNumber = movedInstruction.asConstNumber();
                  Value constantValue = movedInstruction.outValue();
                  for (Instruction user : constantValue.uniqueUsers()) {
                    ConstNumber newCstNum = ConstNumber.copyOf(code, constNumber);
                    newCstNum.setPosition(user.getPosition());
                    InstructionListIterator iterator = user.getBlock().listIterator(code, user);
                    iterator.previous();
                    iterator.add(newCstNum);
                    user.replaceValue(constantValue, newCstNum.outValue());
                  }
                  constantValue.clearUsers();
                  return true;
                });
      }

      // Add constant into the dominator block of usages.
      boolean hasCatchHandlers = block.hasCatchHandlers();
      InstructionListIterator instructionIterator = block.listIterator(code);
      while (instructionIterator.hasNext()) {
        Instruction insertionPoint = instructionIterator.next();
        if (insertionPoint.isJumpInstruction()
            || (hasCatchHandlers && insertionPoint.instructionTypeCanThrow())
            || (options.canHaveCmpIfFloatBug() && insertionPoint.isCmp())) {
          break;
        }
        for (Value use :
            Iterables.concat(insertionPoint.inValues(), insertionPoint.getDebugValues())) {
          Instruction movedInstruction = movedInstructions.remove(use);
          if (movedInstruction != null) {
            instructionIterator =
                insertInstructionWithShortenedLiveRange(
                    code, blockIterator, instructionIterator, movedInstruction, insertionPoint);
          }
        }
      }

      // Insert remaining constant instructions prior to the "exit".
      Instruction insertionPoint = instructionIterator.peekPrevious();
      for (Instruction movedInstruction : movedInstructions.values()) {
        instructionIterator =
            insertInstructionWithShortenedLiveRange(
                code, blockIterator, instructionIterator, movedInstruction, insertionPoint);
      }
    }

    code.removeRedundantBlocks();
  }

  private InstructionListIterator insertInstructionWithShortenedLiveRange(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      Instruction movedInstruction,
      Instruction insertionPoint) {
    Instruction previous = instructionIterator.previous();
    assert previous == insertionPoint;
    movedInstruction.setPosition(
        getPositionForMovedNonThrowingInstruction(movedInstruction, insertionPoint));
    if (movedInstruction.instructionTypeCanThrow()
        && insertionPoint.getBlock().hasCatchHandlers()) {
      // Split the block and reset the block iterator.
      BasicBlock splitBlock =
          instructionIterator.splitCopyCatchHandlers(code, blockIterator, appView.options());
      BasicBlock previousBlock = blockIterator.previousUntil(b -> b == splitBlock);
      assert previousBlock == splitBlock;
      blockIterator.next();

      // Add the constant instruction before the exit instruction.
      assert !instructionIterator.hasNext();
      instructionIterator.previous();
      instructionIterator.add(movedInstruction);

      // Continue insertion at the entry of the split block.
      instructionIterator = splitBlock.listIterator(code);
    } else {
      instructionIterator.add(movedInstruction);
    }
    Instruction next = instructionIterator.next();
    assert next == insertionPoint;
    return instructionIterator;
  }

  private Position getPositionForMovedNonThrowingInstruction(
      Instruction movedInstruction, Instruction insertionPoint) {
    // If the type of the moved instruction is throwing and we don't have a position at the
    // insertion point, we use the special synthetic-none position, which is OK as the moved
    // instruction instance is known not to throw (or we would not be allowed the move it).
    if (movedInstruction.instructionTypeCanThrow() && !insertionPoint.getPosition().isSome()) {
      return Position.syntheticNone();
    }
    return insertionPoint.getPosition();
  }

  private void shortenLiveRangesInsideBlock(
      IRCode code,
      BasicBlock block,
      LazyBox<DominatorTree> dominatorTreeMemoization,
      Map<BasicBlock, LinkedHashMap<Value, Instruction>> addConstantInBlock,
      Predicate<Instruction> selector) {
    InstructionListIterator iterator = block.listIterator(code);
    boolean seenCompareExit = false;
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (options.canHaveCmpIfFloatBug() && instruction.isCmp()) {
        seenCompareExit = true;
      }

      if (instruction.hasUnusedOutValue() || instruction.outValue().hasLocalInfo()) {
        continue;
      }

      if (!selector.test(instruction)) {
        continue;
      }

      // Here we try to stop wasting time in the common case where constants are used immediately
      // after their definition.
      //
      // This is especially important for the creation of large arrays, which has the following code
      // pattern repeated many times, where the two loaded constants are only used by the ArrayPut
      // instruction.
      //
      //   Const number (the array index)
      //   Const (the array entry value)
      //   ArrayPut
      //
      // The heuristic is therefore to check for constants used only once if the use is within the
      // next two instructions, and only swap them if that is the case (cannot shorten the live
      // range anyway).
      if (instruction.outValue().hasSingleUniqueUser() && !instruction.outValue().hasPhiUsers()) {
        Instruction uniqueUse = instruction.outValue().singleUniqueUser();
        Instruction next = iterator.next();
        if (uniqueUse == next) {
          iterator.previous();
          continue;
        }
        if (next.hasOutValue()
            && next.outValue().hasSingleUniqueUser()
            && !next.outValue().hasPhiUsers()
            && iterator.hasNext()) {
          Instruction nextNext = iterator.peekNext();
          Instruction uniqueUseNext = next.outValue().singleUniqueUser();
          if (uniqueUse == nextNext && uniqueUseNext == nextNext) {
            iterator.previous();
            continue;
          }
        }
        iterator.previous();
        // The call to removeOrReplaceByDebugLocalRead() at the end of this method will remove the
        // last returned element of this iterator. Therefore, we re-read this element from the
        // iterator.
        iterator.previous();
        iterator.next();
      }
      // Collect the blocks for all users of the constant.
      Set<BasicBlock> userBlocks = new LinkedHashSet<>();
      for (Instruction user : instruction.outValue().uniqueUsers()) {
        userBlocks.add(user.getBlock());
      }
      for (Phi phi : instruction.outValue().uniquePhiUsers()) {
        int predecessorIndex = 0;
        for (Value operand : phi.getOperands()) {
          if (operand == instruction.outValue()) {
            userBlocks.add(phi.getBlock().getPredecessors().get(predecessorIndex));
          }
          predecessorIndex++;
        }
      }
      // Locate the closest dominator block for all user blocks.
      DominatorTree dominatorTree = dominatorTreeMemoization.computeIfAbsent();
      BasicBlock dominator = dominatorTree.closestDominator(userBlocks);

      // Do not move the constant if the constant instruction can throw and the dominator or the
      // original block has catch handlers, or if the code may have monitor instructions, since this
      // could lead to verification errors.
      if (instruction.instructionTypeCanThrow()) {
        if (block.hasCatchHandlers()
            || dominator.hasCatchHandlers()
            || code.metadata().mayHaveMonitorInstruction()) {
          continue;
        }
      }

      // If the dominator block has a potential compare exit we will chose that as the insertion
      // point. Uniquely for instructions having invalues this can be before the definition of them.
      // Bail-out when this is the case. See b/251015885 for more information.
      if (seenCompareExit
          && Iterables.any(instruction.inValues(), x -> x.getBlock() == dominator)) {
        continue;
      }

      Instruction copy;
      switch (instruction.opcode()) {
        case CONST_CLASS:
          copy = ConstClass.copyOf(code, instruction.asConstClass());
          break;
        case CONST_NUMBER:
          copy = ConstNumber.copyOf(code, instruction.asConstNumber());
          break;
        case CONST_STRING:
          copy = ConstString.copyOf(code, instruction.asConstString());
          break;
        case DEX_ITEM_BASED_CONST_STRING:
          copy = DexItemBasedConstString.copyOf(code, instruction.asDexItemBasedConstString());
          break;
        case INSTANCE_GET:
          copy = InstanceGet.copyOf(code, instruction.asInstanceGet());
          break;
        case STATIC_GET:
          copy = StaticGet.copyOf(code, instruction.asStaticGet());
          break;
        default:
          throw new Unreachable();
      }
      instruction.outValue().replaceUsers(copy.outValue());
      addConstantInBlock
          .computeIfAbsent(dominator, k -> new LinkedHashMap<>())
          .put(copy.outValue(), copy);
      // Using peekPrevious() would disable remove().
      assert iterator.previous() == instruction && iterator.next() == instruction;
      iterator.removeOrReplaceByDebugLocalRead();
    }
  }
}
