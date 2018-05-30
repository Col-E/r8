// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.CfgPrinter;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class IRCode {

  // Stack marker to denote when all successors of a block have been processed when topologically
  // sorting.
  private static class BlockMarker {
    final BasicBlock block;
    BlockMarker(BasicBlock block) {
      this.block = block;
    }
  }

  // When numbering instructions we number instructions only with even numbers. This allows us to
  // use odd instruction numbers for the insertion of moves during spilling.
  public static final int INSTRUCTION_NUMBER_DELTA = 2;

  public final DexEncodedMethod method;

  public LinkedList<BasicBlock> blocks;
  public final ValueNumberGenerator valueNumberGenerator;
  private int usedMarkingColors = 0;

  private boolean numbered = false;
  private int nextInstructionNumber = 0;

  // Initial value indicating if the code does have actual positions on all throwing instructions.
  // If this is the case, which holds for javac code, then we want to ensure that it remains so.
  private boolean allThrowingInstructionsHavePositions;

  public final boolean hasDebugPositions;

  public final InternalOptions options;

  public IRCode(
      InternalOptions options,
      DexEncodedMethod method,
      LinkedList<BasicBlock> blocks,
      ValueNumberGenerator valueNumberGenerator,
      boolean hasDebugPositions) {
    this.options = options;
    this.method = method;
    this.blocks = blocks;
    this.valueNumberGenerator = valueNumberGenerator;
    this.hasDebugPositions = hasDebugPositions;
    allThrowingInstructionsHavePositions = computeAllThrowingInstructionsHavePositions();
  }

  /**
   * Compute the set of live values at the entry to each block using a backwards data-flow analysis.
   */
  public Map<BasicBlock, Set<Value>> computeLiveAtEntrySets() {
    Map<BasicBlock, Set<Value>> liveAtEntrySets = new IdentityHashMap<>();
    Queue<BasicBlock> worklist = new ArrayDeque<>();
    // Since this is a backwards data-flow analysis we process the blocks in reverse
    // topological order to reduce the number of iterations.
    ImmutableList<BasicBlock> sorted = topologicallySortedBlocks();
    worklist.addAll(sorted.reverse());
    while (!worklist.isEmpty()) {
      BasicBlock block = worklist.poll();
      Set<Value> live = new HashSet<>();
      for (BasicBlock succ : block.getSuccessors()) {
        Set<Value> succLiveAtEntry = liveAtEntrySets.get(succ);
        if (succLiveAtEntry != null) {
          live.addAll(succLiveAtEntry);
        }
        int predIndex = succ.getPredecessors().indexOf(block);
        for (Phi phi : succ.getPhis()) {
          live.add(phi.getOperand(predIndex));
          assert phi.getDebugValues().stream().allMatch(Value::needsRegister);
          live.addAll(phi.getDebugValues());
        }
      }
      ListIterator<Instruction> iterator =
          block.getInstructions().listIterator(block.getInstructions().size());
      while (iterator.hasPrevious()) {
        Instruction instruction = iterator.previous();
        if (instruction.outValue() != null) {
          live.remove(instruction.outValue());
        }
        for (Value use : instruction.inValues()) {
          if (use.needsRegister()) {
            live.add(use);
          }
        }
        assert instruction.getDebugValues().stream().allMatch(Value::needsRegister);
        live.addAll(instruction.getDebugValues());
      }
      for (Phi phi : block.getPhis()) {
        live.remove(phi);
      }
      Set<Value> previousLiveAtEntry = liveAtEntrySets.put(block, live);
      // If the live-at-entry set changed, add the predecessors to the worklist if they are not
      // already there.
      if (previousLiveAtEntry == null || !previousLiveAtEntry.equals(live)) {
        for (BasicBlock pred : block.getPredecessors()) {
          if (!worklist.contains(pred)) {
            worklist.add(pred);
          }
        }
      }
    }
    assert liveAtEntrySets.get(sorted.get(0)).size() == 0
        : "Unexpected values live at entry to first block: " + liveAtEntrySets.get(sorted.get(0));
    return liveAtEntrySets;
  }

  public void splitCriticalEdges() {
    List<BasicBlock> newBlocks = new ArrayList<>();
    int nextBlockNumber = getHighestBlockNumber() + 1;
    for (BasicBlock block : blocks) {
      // We are using a spilling register allocator that might need to insert moves at
      // all critical edges, so we always split them all.
      List<BasicBlock> predecessors = block.getPredecessors();
      if (predecessors.size() <= 1) {
        continue;
      }
      // If any of the edges to the block are critical, we need to insert new blocks on each
      // containing the move-exception instruction which must remain the first instruction.
      if (block.entry() instanceof MoveException) {
        nextBlockNumber = block.splitCriticalExceptionEdges(
            nextBlockNumber, valueNumberGenerator, newBlocks::add);
        continue;
      }
      for (int predIndex = 0; predIndex < predecessors.size(); predIndex++) {
        BasicBlock pred = predecessors.get(predIndex);
        if (!pred.hasOneNormalExit()) {
          // Critical edge: split it and inject a new block into which the
          // phi moves can be inserted. The new block is created with the
          // correct predecessor and successor structure. It is inserted
          // at the end of the list of blocks disregarding branching
          // structure.
          BasicBlock newBlock = BasicBlock.createGotoBlock(nextBlockNumber++, block);
          newBlocks.add(newBlock);
          pred.replaceSuccessor(block, newBlock);
          newBlock.getPredecessors().add(pred);
          predecessors.set(predIndex, newBlock);
        }
      }
    }
    blocks.addAll(newBlocks);
  }

  /**
   * Trace blocks and attempt to put fallthrough blocks immediately after the block that
   * falls through. When we fail to do that we create a new fallthrough block with an explicit
   * goto to the actual fallthrough block.
   */
  public void traceBlocks() {
    // Get the blocks first, as calling topologicallySortedBlocks also sets marks.
    ImmutableList<BasicBlock> sorted = topologicallySortedBlocks();
    int color = reserveMarkingColor();
    int nextBlockNumber = blocks.size();
    LinkedList<BasicBlock> tracedBlocks = new LinkedList<>();
    for (BasicBlock block : sorted) {
      if (!block.isMarked(color)) {
        block.mark(color);
        tracedBlocks.add(block);
        BasicBlock current = block;
        BasicBlock fallthrough = block.exit().fallthroughBlock();
        while (fallthrough != null && !fallthrough.isMarked(color)) {
          fallthrough.mark(color);
          tracedBlocks.add(fallthrough);
          current = fallthrough;
          fallthrough = fallthrough.exit().fallthroughBlock();
        }
        if (fallthrough != null) {
          BasicBlock newFallthrough = BasicBlock.createGotoBlock(nextBlockNumber++, fallthrough);
          current.exit().setFallthroughBlock(newFallthrough);
          newFallthrough.getPredecessors().add(current);
          fallthrough.replacePredecessor(current, newFallthrough);
          newFallthrough.mark(color);
          tracedBlocks.add(newFallthrough);
        }
      }
    }
    blocks = tracedBlocks;
    returnMarkingColor(color);
    assert verifyNoColorsInUse();
  }

  private void ensureBlockNumbering() {
    if (!numbered) {
      numbered = true;
      int blockNumber = 0;
      for (BasicBlock block : topologicallySortedBlocks()) {
        block.setNumber(blockNumber++);
      }
    }
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("blocks:\n");
    for (BasicBlock block : blocks) {
      builder.append(block.toDetailedString());
      builder.append("\n");
    }
    return builder.toString();
  }

  public void clearMarks(int color) {
    for (BasicBlock block : blocks) {
      block.clearMark(color);
    }
  }

  public void removeMarkedBlocks(int color) {
    ListIterator<BasicBlock> blockIterator = listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (block.isMarked(color)) {
        blockIterator.remove();
      }
    }
  }

  private boolean verifyNoBlocksMarked(int color) {
    for (BasicBlock block : blocks) {
      if (block.isMarked(color)) {
        return false;
      }
    }
    return true;
  }


  public void removeBlocks(List<BasicBlock> blocksToRemove) {
    blocks.removeAll(blocksToRemove);
  }

  /**
   * Compute quasi topologically sorted list of the basic blocks using depth first search.
   *
   * TODO(ager): We probably want to compute strongly connected components and topologically
   * sort strongly connected components instead. However, this is much better than having
   * no sorting.
   */
  public ImmutableList<BasicBlock> topologicallySortedBlocks() {
    ImmutableList<BasicBlock> ordered = depthFirstSorting();
    return options.testing.placeExceptionalBlocksLast
        ? reorderExceptionalBlocksLastForTesting(ordered)
        : ordered;
  }

  private ImmutableList<BasicBlock> depthFirstSorting() {
    ArrayList<BasicBlock> reverseOrdered = new ArrayList<>(blocks.size());
    Set<BasicBlock> visitedBlocks = new HashSet<>(blocks.size());
    Deque<Object> worklist = new ArrayDeque<>(blocks.size());
    worklist.addLast(blocks.getFirst());
    while (!worklist.isEmpty()) {
      Object item = worklist.removeLast();
      if (item instanceof BlockMarker) {
        reverseOrdered.add(((BlockMarker) item).block);
        continue;
      }
      BasicBlock block = (BasicBlock) item;
      if (!visitedBlocks.contains(block)) {
        visitedBlocks.add(block);
        worklist.addLast(new BlockMarker(block));
        for (int i = block.getSuccessors().size() - 1; i >= 0; i--) {
          worklist.addLast(block.getSuccessors().get(i));
        }
      }
    }
    ImmutableList.Builder<BasicBlock> builder = ImmutableList.builder();
    for (int i = reverseOrdered.size() - 1; i >= 0; i--) {
      builder.add(reverseOrdered.get(i));
    }
    return builder.build();
  }

  // Reorder the blocks forcing all exceptional blocks to be at the end.
  private static ImmutableList<BasicBlock> reorderExceptionalBlocksLastForTesting(
      ImmutableList<BasicBlock> blocks) {
    ImmutableList.Builder<BasicBlock> reordered = ImmutableList.builder();
    for (BasicBlock block : blocks) {
      if (!block.entry().isMoveException()) {
        reordered.add(block);
      }
    }
    for (BasicBlock block : blocks) {
      if (block.entry().isMoveException()) {
        reordered.add(block);
      }
    }
    return reordered.build();
  }

  public void print(CfgPrinter printer) {
    ensureBlockNumbering();
    for (BasicBlock block : blocks) {
      block.print(printer);
    }
  }

  public boolean isConsistentSSA() {
    assert isConsistentGraph();
    assert consistentDefUseChains();
    assert validThrowingInstructions();
    assert noCriticalEdges();
    return true;
  }

  public boolean isConsistentGraph() {
    assert verifyNoColorsInUse();
    assert consistentBlockNumbering();
    assert consistentPredecessorSuccessors();
    assert consistentCatchHandlers();
    assert consistentBlockInstructions();
    assert !allThrowingInstructionsHavePositions || computeAllThrowingInstructionsHavePositions();
    return true;
  }

  private boolean noCriticalEdges() {
    for (BasicBlock block : blocks) {
      List<BasicBlock> predecessors = block.getPredecessors();
      if (predecessors.size() <= 1) {
        continue;
      }
      if (block.entry() instanceof MoveException) {
        assert false;
        return false;
      }
      for (int predIndex = 0; predIndex < predecessors.size(); predIndex++) {
        if (!predecessors.get(predIndex).hasOneNormalExit()) {
          assert false;
          return false;
        }
      }
    }
    return true;
  }

  public boolean hasCatchHandlers() {
    for (BasicBlock block : blocks) {
      if (block.hasCatchHandlers()) {
        return true;
      }
    }
    return false;
  }

  private boolean consistentDefUseChains() {
    Set<Value> values = new HashSet<>();

    for (BasicBlock block : blocks) {
      int predecessorCount = block.getPredecessors().size();
      // Check that all phi uses are consistent.
      for (Phi phi : block.getPhis()) {
        assert !phi.isTrivialPhi();
        assert phi.getOperands().size() == predecessorCount;
        assert phi.outType() != ValueType.INT_OR_FLOAT_OR_NULL;
        values.add(phi);
        for (Value value : phi.getOperands()) {
          values.add(value);
          assert value.uniquePhiUsers().contains(phi);
        }
        for (Value value : phi.getDebugValues()) {
          values.add(value);
          assert value.debugPhiUsers().contains(phi);
        }
      }
      for (Instruction instruction : block.getInstructions()) {
        assert instruction.getBlock() == block;
        Value outValue = instruction.outValue();
        if (outValue != null) {
          values.add(outValue);
          assert outValue.definition == instruction;
          assert outValue.outType() != ValueType.INT_OR_FLOAT_OR_NULL;
        }
        for (Value value : instruction.inValues()) {
          values.add(value);
          assert value.uniqueUsers().contains(instruction);
        }
        for (Value value : instruction.getDebugValues()) {
          values.add(value);
          assert value.debugUsers().contains(instruction);
        }
      }
    }

    for (Value value : values) {
      assert verifyValue(value);
      assert consistentValueUses(value);
    }

    return true;
  }

  private boolean verifyValue(Value value) {
    assert value.isPhi() ? verifyPhi(value.asPhi()) : verifyDefinition(value);
    return true;
  }

  private boolean verifyPhi(Phi phi) {
    assert phi.getBlock().getPhis().contains(phi);
    return true;
  }

  private boolean verifyDefinition(Value value) {
    assert value.definition.outValue() == value;
    return true;
  }

  private boolean consistentValueUses(Value value) {
    for (Instruction user : value.uniqueUsers()) {
      assert user.inValues().contains(value);
    }
    for (Phi phiUser : value.uniquePhiUsers()) {
      assert phiUser.getOperands().contains(value);
      assert phiUser.getBlock().getPhis().contains(phiUser);
    }
    if (value.hasLocalInfo()) {
      for (Instruction debugUser : value.debugUsers()) {
        assert debugUser.getDebugValues().contains(value);
      }
      for (Phi phiUser : value.debugPhiUsers()) {
        assert verifyPhi(phiUser);
        assert phiUser.getDebugValues().contains(value);
      }
    }
    return true;
  }

  private boolean consistentPredecessorSuccessors() {
    for (BasicBlock block : blocks) {
      // Check that all successors are distinct.
      assert new HashSet<>(block.getSuccessors()).size() == block.getSuccessors().size();
      for (BasicBlock succ : block.getSuccessors()) {
        // Check that successors are in the block list.
        assert blocks.contains(succ);
        // Check that successors have this block as a predecessor.
        assert succ.getPredecessors().contains(block);
      }
      // Check that all predecessors are distinct.
      assert new HashSet<>(block.getPredecessors()).size() == block.getPredecessors().size();
      for (BasicBlock pred : block.getPredecessors()) {
        // Check that predecessors are in the block list.
        assert blocks.contains(pred);
        // Check that predecessors have this block as a successor.
        assert pred.getSuccessors().contains(block);
      }
    }
    return true;
  }

  private boolean consistentCatchHandlers() {
    for (BasicBlock block : blocks) {
      // Check that catch handlers are always the first successors of a block.
      if (block.hasCatchHandlers()) {
        assert block.exit().isGoto() || block.exit().isThrow();
        CatchHandlers<Integer> catchHandlers = block.getCatchHandlersWithSuccessorIndexes();
        // If there is a catch-all guard it must be the last.
        List<DexType> guards = catchHandlers.getGuards();
        int lastGuardIndex = guards.size() - 1;
        for (int i = 0; i < guards.size(); i++) {
          assert guards.get(i) != DexItemFactory.catchAllType || i == lastGuardIndex;
        }
        // Check that all successors except maybe the last are catch successors.
        List<Integer> sortedHandlerIndices = new ArrayList<>(catchHandlers.getAllTargets());
        sortedHandlerIndices.sort(Comparator.naturalOrder());
        int firstIndex = sortedHandlerIndices.get(0);
        int lastIndex = sortedHandlerIndices.get(sortedHandlerIndices.size() - 1);
        assert firstIndex == 0;
        assert lastIndex < sortedHandlerIndices.size();
        int lastSuccessorIndex = block.getSuccessors().size() - 1;
        assert lastIndex == lastSuccessorIndex  // All successors are catch successors.
            || lastIndex == lastSuccessorIndex - 1; // All but one successors are catch successors.
        assert lastIndex == lastSuccessorIndex || !block.exit().isThrow();
      }
    }
    return true;
  }

  public boolean consistentBlockNumbering() {
    return blocks.stream()
        .collect(Collectors.groupingBy(BasicBlock::getNumber, Collectors.counting()))
        .entrySet().stream().noneMatch((bb2count) -> bb2count.getValue() > 1);
  }

  private boolean consistentBlockInstructions() {
    boolean argumentsAllowed = true;
    for (BasicBlock block : blocks) {
      block.consistentBlockInstructions(argumentsAllowed);
      argumentsAllowed = false;
    }
    return true;
  }

  private boolean validThrowingInstructions() {
    for (BasicBlock block : blocks) {
      if (block.hasCatchHandlers()) {
        boolean seenThrowing = false;
        for (Instruction instruction : block.getInstructions()) {
          if (instruction.instructionTypeCanThrow()) {
            assert !seenThrowing;
            seenThrowing = true;
            continue;
          }
          // After the throwing instruction only debug instructions and the final jump
          // instruction is allowed.
          // TODO(ager): For now allow const instructions due to the way consts are pushed
          // towards their use
          if (seenThrowing) {
            assert instruction.isDebugInstruction()
                || instruction.isJumpInstruction()
                || instruction.isConstInstruction()
                || instruction.isNewArrayFilledData()
                || instruction.isStore()
                || instruction.isPop();
          }
        }
      }
    }
    return true;
  }

  public InstructionIterator instructionIterator() {
    return new IRCodeInstructionsIterator(this);
  }

  public ImmutableList<BasicBlock> computeNormalExitBlocks() {
    ImmutableList.Builder<BasicBlock> builder = ImmutableList.builder();
    for (BasicBlock block : blocks) {
      if (block.exit().isReturn()) {
        builder.add(block);
      }
    }
    return builder.build();
  }

  public ListIterator<BasicBlock> listIterator() {
    return new BasicBlockIterator(this);
  }

  public ListIterator<BasicBlock> listIterator(int index) {
    return new BasicBlockIterator(this, index);
  }

  public ImmutableList<BasicBlock> numberInstructions() {
    ImmutableList<BasicBlock> blocks = topologicallySortedBlocks();
    for (BasicBlock block : blocks) {
      nextInstructionNumber = block.numberInstructions(nextInstructionNumber);
    }
    return blocks;
  }

  public int numberRemainingInstructions() {
    InstructionIterator it = instructionIterator();
    while (it.hasNext()) {
      Instruction i = it.next();
      if (i.getNumber() == -1) {
        i.setNumber(nextInstructionNumber);
        nextInstructionNumber += INSTRUCTION_NUMBER_DELTA;
      }
    }
    return nextInstructionNumber;
  }

  public int getNextInstructionNumber() {
    return nextInstructionNumber;
  }

  public List<Value> collectArguments() {
    return collectArguments(false);
  }

  public List<Value> collectArguments(boolean ignoreReceiver) {
    final List<Value> arguments = new ArrayList<>();
    Iterator<Instruction> iterator = blocks.get(0).iterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (instruction.isArgument()) {
        Value out = instruction.asArgument().outValue();
        if (!ignoreReceiver || !out.isThis()) {
          arguments.add(out);
        }
      }
    }
    assert arguments.size()
        == method.method.getArity() + ((method.accessFlags.isStatic() || ignoreReceiver) ? 0 : 1);
    return arguments;
  }

  public Value getThis() {
    if (method.accessFlags.isStatic()) {
      return null;
    }
    Instruction firstArg = blocks.getFirst().listIterator().nextUntil(Instruction::isArgument);
    assert firstArg != null;
    Value thisValue = firstArg.asArgument().outValue();
    assert thisValue.isThis();
    return thisValue;
  }

  public Value createValue(ValueType valueType, DebugLocalInfo local) {
    return new Value(valueNumberGenerator.next(), valueType, local);
  }

  public Value createValue(ValueType valueType) {
    return createValue(valueType, null);
  }

  public ConstNumber createIntConstant(int value) {
    return new ConstNumber(createValue(ValueType.INT), value);
  }

  public ConstNumber createTrue() {
    return new ConstNumber(createValue(ValueType.INT), 1);
  }

  public ConstNumber createFalse() {
    return new ConstNumber(createValue(ValueType.INT), 0);
  }

  public final int getHighestBlockNumber() {
    return blocks.stream().max(Comparator.comparingInt(BasicBlock::getNumber)).get().getNumber();
  }

  public Instruction createConstNull(Instruction from) {
    return new ConstNumber(createValue(from.outType()), 0);
  }

  public boolean doAllThrowingInstructionsHavePositions() {
    return allThrowingInstructionsHavePositions;
  }

  public void setAllThrowingInstructionsHavePositions(boolean value) {
    this.allThrowingInstructionsHavePositions = value;
  }

  private boolean computeAllThrowingInstructionsHavePositions() {
    InstructionIterator it = instructionIterator();
    while (it.hasNext()) {
      Instruction instruction = it.next();
      if (instruction.instructionTypeCanThrow() &&
          !instruction.isConstString() &&
          instruction.getPosition().isNone()) {
        return false;
      }
    }
    return true;
  }

  public void removeAllTrivialPhis() {
    for (BasicBlock block : blocks) {
      List<Phi> phis = new ArrayList<>(block.getPhis());
      for (Phi phi : phis) {
        phi.removeTrivialPhi();
      }
    }
  }

  public int reserveMarkingColor() {
    int color = 1;
    while ((usedMarkingColors & color) == color) {
      assert color <= 0x40000000;
      color <<= 1;
    }
    // TODO(sgjesse): Remove this assert if more colors will be required.
    assert color == 1;
    assert verifyNoBlocksMarked(color);
    usedMarkingColors |= color;
    return color;
  }

  public void returnMarkingColor(int color) {
    assert (usedMarkingColors | color) == color;
    clearMarks(color);
    usedMarkingColors &= ~color;
  }

  public boolean verifyNoColorsInUse() {
    return usedMarkingColors == 0;
  }

  public void removeUnreachableBlocks() {
    int color = reserveMarkingColor();
    Queue<BasicBlock> worklist = new ArrayDeque<>();
    worklist.add(blocks.getFirst());
    while (!worklist.isEmpty()) {
      BasicBlock block = worklist.poll();
      if (block.isMarked(color)) {
        continue;
      }
      block.mark(color);
      for (BasicBlock successor : block.getSuccessors()) {
        if (!successor.isMarked(color)) {
          worklist.add(successor);
        }
      }
    }
    ListIterator<BasicBlock> blockIterator = listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock current = blockIterator.next();
      if (!current.isMarked(color)) {
        current.cleanForRemoval();
        blockIterator.remove();
      }
    }
    returnMarkingColor(color);
  }
}
