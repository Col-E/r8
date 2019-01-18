// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.analysis.TypeChecker;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.CfgPrinter;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class IRCode {

  private static final int MAX_MARKING_COLOR = 0x40000000;

  public static class LiveAtEntrySets {
    // Set of live SSA values (regardless of whether they denote a local variable).
    public final Set<Value> liveValues;

    // Subset of live local-variable values.
    public final Set<Value> liveLocalValues;

    public final Deque<Value> liveStackValues;

    public LiveAtEntrySets(
        Set<Value> liveValues, Set<Value> liveLocalValues, Deque<Value> liveStackValues) {
      assert liveValues.containsAll(liveLocalValues);
      this.liveValues = liveValues;
      this.liveLocalValues = liveLocalValues;
      this.liveStackValues = liveStackValues;
    }

    @Override
    public int hashCode() {
      throw new Unreachable();
    }

    @Override
    public boolean equals(Object o) {
      LiveAtEntrySets other = (LiveAtEntrySets) o;
      return liveValues.equals(other.liveValues) && liveLocalValues.equals(other.liveLocalValues);
    }

    public boolean isEmpty() {
      return liveValues.isEmpty() && liveLocalValues.isEmpty();
    }
  }

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

  // TODO(b/122257895): Update OptimizationInfo to capture instruction kinds of interest.
  public final boolean hasDebugPositions;
  public boolean hasConstString;
  public final boolean hasMonitorInstruction;

  public final InternalOptions options;

  public final Origin origin;

  public IRCode(
      InternalOptions options,
      DexEncodedMethod method,
      LinkedList<BasicBlock> blocks,
      ValueNumberGenerator valueNumberGenerator,
      boolean hasDebugPositions,
      boolean hasMonitorInstruction,
      boolean hasConstString,
      Origin origin) {
    assert options != null;
    this.options = options;
    this.method = method;
    this.blocks = blocks;
    this.valueNumberGenerator = valueNumberGenerator;
    this.hasDebugPositions = hasDebugPositions;
    this.hasMonitorInstruction = hasMonitorInstruction;
    this.hasConstString = hasConstString;
    this.origin = origin;
    // TODO(zerny): Remove or update this property now that all instructions have positions.
    allThrowingInstructionsHavePositions = computeAllThrowingInstructionsHavePositions();
  }

  public void copyMetadataFromInlinee(IRCode inlinee) {
    assert !inlinee.hasMonitorInstruction;
    this.hasConstString |= inlinee.hasConstString;
  }

  /**
   * Compute the set of live values at the entry to each block using a backwards data-flow analysis.
   */
  public Map<BasicBlock, LiveAtEntrySets> computeLiveAtEntrySets() {
    Map<BasicBlock, LiveAtEntrySets> liveAtEntrySets = new IdentityHashMap<>();
    Queue<BasicBlock> worklist = new ArrayDeque<>();
    // Since this is a backwards data-flow analysis we process the blocks in reverse
    // topological order to reduce the number of iterations.
    ImmutableList<BasicBlock> sorted = topologicallySortedBlocks();
    worklist.addAll(sorted.reverse());
    while (!worklist.isEmpty()) {
      BasicBlock block = worklist.poll();
      Set<Value> live = new HashSet<>();
      Set<Value> liveLocals = new HashSet<>();
      Deque<Value> liveStack = new ArrayDeque<>();
      Set<BasicBlock> exceptionalSuccessors = block.getCatchHandlers().getUniqueTargets();
      for (BasicBlock succ : block.getSuccessors()) {
        LiveAtEntrySets liveAtSucc = liveAtEntrySets.get(succ);
        if (liveAtSucc != null) {
          live.addAll(liveAtSucc.liveValues);
          liveLocals.addAll(liveAtSucc.liveLocalValues);
          // The stack is only allowed to be non-empty in the case of linear-flow (so-far).
          // If succ is an exceptional successor the successor stack should be empty
          // otherwise only one successor must have a non-empty stack.
          if (exceptionalSuccessors.contains(succ)) {
            assert liveAtSucc.liveStackValues.size() == 0;
          } else {
            assert liveStack.isEmpty();
            liveStack = new ArrayDeque<>(liveAtSucc.liveStackValues);
          }
        }
        int predIndex = succ.getPredecessors().indexOf(block);
        for (Phi phi : succ.getPhis()) {
          Value operand = phi.getOperand(predIndex);
          if (operand.isValueOnStack()) {
            liveStack.addLast(operand);
          } else {
            live.add(operand);
            if (phi.hasLocalInfo()) {
              // If the phi has local information that implies that the local *must* be live at
              // entry to the block (ie, phis can't end a local explicitly only instructions can).
              // The graph also requires that any local live at entry to a block is at the latest
              // started in the split edge prior to that block (ie, phis cannot start a local
              // range).
              // Therefore, if the phi has local information, that local is live and the operand
              // must be live at block exit.
              assert phi.getLocalInfo() == operand.getLocalInfo();
              liveLocals.add(operand);
            }
          }
        }
      }
      // Sanity-check, making sure that if the stack is non-empty only in the case of a single
      // normal successor.
      assert liveStack.isEmpty()
          || block.getSuccessors().size() - exceptionalSuccessors.size() == 1;
      Iterator<Instruction> iterator = block.getInstructions().descendingIterator();
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        Value outValue = instruction.outValue();
        if (outValue != null) {
          if (outValue instanceof StackValue) {
            Value pop = liveStack.removeLast();
            assert pop == outValue;
          } else if (outValue instanceof StackValues) {
            StackValue[] values = ((StackValues) outValue).getStackValues();
            for (int i = values.length - 1; i >= 0; i--) {
              Value pop = liveStack.removeLast();
              assert pop == values[i];
            }
          } else {
            live.remove(outValue);
            assert outValue.hasLocalInfo() || !liveLocals.contains(outValue);
            if (outValue.hasLocalInfo()) {
              liveLocals.remove(outValue);
            }
          }
        }
        for (Value use : instruction.inValues()) {
          if (use.needsRegister()) {
            live.add(use);
          } else if (use.isValueOnStack()) {
            liveStack.addLast(use);
          }
        }
        assert instruction.getDebugValues().stream().allMatch(Value::needsRegister);
        assert instruction.getDebugValues().stream().allMatch(Value::hasLocalInfo);
        live.addAll(instruction.getDebugValues());
        liveLocals.addAll(instruction.getDebugValues());
      }
      for (Phi phi : block.getPhis()) {
        if (phi.isValueOnStack()) {
          liveStack.remove(phi);
        } else {
          live.remove(phi);
        }
        assert phi.hasLocalInfo() || !liveLocals.contains(phi);
        if (phi.hasLocalInfo()) {
          liveLocals.remove(phi);
        }
      }
      LiveAtEntrySets liveAtEntry = new LiveAtEntrySets(live, liveLocals, liveStack);
      LiveAtEntrySets previousLiveAtEntry = liveAtEntrySets.put(block, liveAtEntry);
      // If the live-at-entry set changed, add the predecessors to the worklist if they are not
      // already there.
      if (previousLiveAtEntry == null || !previousLiveAtEntry.equals(liveAtEntry)) {
        for (BasicBlock pred : block.getPredecessors()) {
          if (!worklist.contains(pred)) {
            worklist.add(pred);
          }
        }
      }
    }
    assert liveAtEntrySets.get(sorted.get(0)).isEmpty()
        : "Unexpected values live at entry to first block: "
        + liveAtEntrySets.get(sorted.get(0)).liveValues;
    return liveAtEntrySets;
  }

  public void splitCriticalEdges() {
    List<BasicBlock> newBlocks = new ArrayList<>();
    int nextBlockNumber = getHighestBlockNumber() + 1;
    for (BasicBlock block : blocks) {
      // We are using a spilling register allocator that might need to insert moves at
      // all critical edges, so we always split them all.
      List<BasicBlock> predecessors = block.getMutablePredecessors();
      if (predecessors.size() <= 1) {
        continue;
      }

      // Exceptional edges are given unique header blocks and can have at most one predecessor.
      assert !block.entry().isMoveException();

      for (int predIndex = 0; predIndex < predecessors.size(); predIndex++) {
        BasicBlock pred = predecessors.get(predIndex);
        if (!pred.hasOneNormalExit()) {
          // Critical edge: split it and inject a new block into which the
          // phi moves can be inserted. The new block is created with the
          // correct predecessor and successor structure. It is inserted
          // at the end of the list of blocks disregarding branching
          // structure.
          BasicBlock newBlock =
              BasicBlock.createGotoBlock(nextBlockNumber++, pred.exit().getPosition(), block);
          newBlocks.add(newBlock);
          pred.replaceSuccessor(block, newBlock);
          newBlock.getMutablePredecessors().add(pred);
          predecessors.set(predIndex, newBlock);
        }
      }
    }
    blocks.addAll(newBlocks);
  }

  public boolean verifySplitCriticalEdges() {
    for (BasicBlock block : blocks) {
      // If there are multiple incoming edges, check each has a split block.
      List<BasicBlock> predecessors = block.getPredecessors();
      if (predecessors.size() > 1) {
        for (BasicBlock predecessor : predecessors) {
          assert predecessor.hasOneNormalExit();
          assert predecessor.getSuccessors().get(0) == block;
        }
      }
      // If there are outgoing exceptional edges, check that each has a split block.
      if (block.hasCatchHandlers()) {
        for (BasicBlock handler : block.getCatchHandlers().getUniqueTargets()) {
          assert handler.getPredecessors().size() == 1;
          assert handler.getPredecessors().get(0) == block;
        }
      }
    }
    return true;
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
          BasicBlock newFallthrough =
              BasicBlock.createGotoBlock(
                  nextBlockNumber++, current.exit().getPosition(), fallthrough);
          current.exit().setFallthroughBlock(newFallthrough);
          newFallthrough.getMutablePredecessors().add(current);
          fallthrough.replacePredecessor(current, newFallthrough);
          newFallthrough.mark(color);
          tracedBlocks.add(newFallthrough);
        }
      }
    }
    blocks = tracedBlocks;
    returnMarkingColor(color);
    assert noColorsInUse();
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

  public void removeBlocks(Collection<BasicBlock> blocksToRemove) {
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
    assert verifyNoImpreciseOrBottomTypes();
    return true;
  }

  public boolean isConsistentGraph() {
    assert noColorsInUse();
    assert consistentBlockNumbering();
    assert consistentPredecessorSuccessors();
    assert consistentCatchHandlers();
    assert consistentBlockInstructions();
    assert !allThrowingInstructionsHavePositions || computeAllThrowingInstructionsHavePositions();
    return true;
  }

  public boolean verifyTypes(
      AppInfo appInfo, AppView<? extends AppInfo> appView, GraphLense graphLense) {
    // We can only type check the program if we have subtyping information. Therefore, we do not
    // require that the program type checks in D8.
    if (appView != null && appView.enableWholeProgramOptimizations()) {
      assert new TypeChecker(appView).check(this);
    }
    assert blocks.stream().allMatch(block -> block.verifyTypes(appInfo, graphLense));
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
        values.add(phi);
        for (Value value : phi.getOperands()) {
          values.add(value);
          assert value.uniquePhiUsers().contains(phi);
          assert !phi.hasLocalInfo() || phi.getLocalInfo() == value.getLocalInfo();
        }
      }
      for (Instruction instruction : block.getInstructions()) {
        assert instruction.getBlock() == block;
        Value outValue = instruction.outValue();
        if (outValue != null) {
          values.add(outValue);
          assert outValue.definition == instruction;
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
      assert block.consistentCatchHandlers();
    }
    return true;
  }

  public boolean consistentBlockNumbering() {
    blocks
        .stream()
        .collect(Collectors.groupingBy(BasicBlock::getNumber, Collectors.counting()))
        .forEach(
            (key, value) -> {
              assert value == 1;
            });
    return true;
  }

  private boolean consistentBlockInstructions() {
    boolean argumentsAllowed = true;
    for (BasicBlock block : blocks) {
      block.consistentBlockInstructions(
          argumentsAllowed,
          options.debug || method.getOptimizationInfo().isReachabilitySensitive());
      argumentsAllowed = false;
    }
    return true;
  }

  private boolean validThrowingInstructions() {
    for (BasicBlock block : blocks) {
      if (block.hasCatchHandlers()) {
        for (BasicBlock handler : block.getCatchHandlers().getUniqueTargets()) {
          // Ensure that catch handlers are always split edges for a well-formed SSA graph.
          assert handler.getPredecessors().size() == 1;
        }
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
                || instruction.isStore()
                || instruction.isPop();
          }
        }
      }
    }
    return true;
  }

  public boolean verifyNoImpreciseOrBottomTypes() {
    return verifySSATypeLattice(
        v -> {
          assert v.getTypeLattice().isPreciseType();
          assert !v.getTypeLattice().isFineGrainedType();
          // For now we assume no bottom types on IR values. We may want to reconsider this for
          // representing unreachable code.
          assert !v.getTypeLattice().isBottom();
          assert !(v.definition instanceof ImpreciseMemberTypeInstruction)
              || ((ImpreciseMemberTypeInstruction) v.definition).getMemberType().isPrecise();
          return true;
        });
  }

  private boolean verifySSATypeLattice(Predicate<Value> tester) {
    for (BasicBlock block : blocks) {
      for (Instruction instruction : block.getInstructions()) {
        Value outValue = instruction.outValue();
        if (outValue != null) {
          assert tester.test(outValue);
        }
      }
      for (Phi phi : block.getPhis()) {
        assert tester.test(phi);
      }
    }
    return true;
  }

  public InstructionIterator instructionIterator() {
    return new IRCodeInstructionsIterator(this);
  }

  public List<BasicBlock> computeNormalExitBlocks() {
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

  public Value createValue(TypeLatticeElement typeLattice, DebugLocalInfo local) {
    return new Value(valueNumberGenerator.next(), typeLattice, local);
  }

  public Value createValue(TypeLatticeElement typeLattice) {
    return createValue(typeLattice, null);
  }

  public Value createValue(DebugLocalInfo local) {
    return createValue(TypeLatticeElement.BOTTOM, local);
  }

  public ConstNumber createIntConstant(int value) {
    Value out = createValue(TypeLatticeElement.INT);
    return new ConstNumber(out, value);
  }

  public final int getHighestBlockNumber() {
    return blocks.stream().max(Comparator.comparingInt(BasicBlock::getNumber)).get().getNumber();
  }

  public ConstNumber createConstNull() {
    Value out = createValue(TypeLatticeElement.NULL);
    return new ConstNumber(out, 0);
  }

  public ConstNumber createConstNull(DebugLocalInfo local) {
    Value out = createValue(TypeLatticeElement.NULL, local);
    return new ConstNumber(out, 0);
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
      if (instruction.instructionTypeCanThrow()
          && !instruction.isConstString()
          && !instruction.isDexItemBasedConstString()
          && instruction.getPosition().isNone()) {
        return false;
      }
    }
    return true;
  }

  public void removeAllTrivialPhis() {
    removeAllTrivialPhis(null);
  }

  public void removeAllTrivialPhis(IRBuilder builder) {
    for (BasicBlock block : blocks) {
      List<Phi> phis = new ArrayList<>(block.getPhis());
      for (Phi phi : phis) {
        phi.removeTrivialPhi(builder);
      }
    }
  }

  public int reserveMarkingColor() {
    assert anyMarkingColorAvailable();
    int color = 1;
    while ((usedMarkingColors & color) == color) {
      assert color <= MAX_MARKING_COLOR;
      color <<= 1;
    }
    usedMarkingColors |= color;
    assert isMarkingColorInUse(color);
    assert verifyNoBlocksMarked(color);
    return color;
  }

  public boolean anyMarkingColorAvailable() {
    int color = 1;
    while ((usedMarkingColors & color) == color) {
      if (color > MAX_MARKING_COLOR) {
        return false;
      }
      color <<= 1;
    }
    return true;
  }

  public void returnMarkingColor(int color) {
    assert isMarkingColorInUse(color);
    clearMarks(color);
    usedMarkingColors &= ~color;
  }

  public boolean isMarkingColorInUse(int color) {
    return (usedMarkingColors & color) != 0;
  }

  public boolean anyBlocksMarkedWithColor(int color) {
    for (BasicBlock block : blocks) {
      if (block.isMarked(color)) {
        return true;
      }
    }
    return false;
  }

  public boolean noColorsInUse() {
    return usedMarkingColors == 0;
  }

  public Set<BasicBlock> getUnreachableBlocks() {
    Set<BasicBlock> unreachableBlocks = Sets.newIdentityHashSet();
    int color = reserveMarkingColor();
    markReachableBlocks(color);
    for (BasicBlock block : blocks) {
      if (!block.isMarked(color)) {
        unreachableBlocks.add(block);
      }
    }
    returnMarkingColor(color);
    return unreachableBlocks;
  }

  public Set<Value> removeUnreachableBlocks() {
    ImmutableSet.Builder<Value> affectedValueBuilder = ImmutableSet.builder();
    int color = reserveMarkingColor();
    markReachableBlocks(color);
    ListIterator<BasicBlock> blockIterator = listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock current = blockIterator.next();
      if (!current.isMarked(color)) {
        affectedValueBuilder.addAll(current.cleanForRemoval());
        blockIterator.remove();
      }
    }
    returnMarkingColor(color);
    return affectedValueBuilder.build();
  }

  // Note: It is the responsibility of the caller to return the marking color.
  private void markReachableBlocks(int color) {
    assert isMarkingColorInUse(color) && !anyBlocksMarkedWithColor(color);
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
  }
}
