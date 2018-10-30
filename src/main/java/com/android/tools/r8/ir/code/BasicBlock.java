// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import static com.android.tools.r8.ir.code.IRCode.INSTRUCTION_NUMBER_DELTA;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DebugLocalInfo.PrintLevel;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.Phi.RegisterReadType;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.utils.CfgPrinter;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Basic block abstraction.
 */
public class BasicBlock {

  private Int2ReferenceMap<DebugLocalInfo> localsAtEntry;

  public boolean consistentBlockInstructions(boolean argumentsAllowed, boolean debug) {
    for (Instruction instruction : getInstructions()) {
      assert instruction.verifyValidPositionInfo(debug);
      assert instruction.getBlock() == this;
      assert !instruction.isArgument() || argumentsAllowed;
      assert !instruction.isDebugLocalRead() || !instruction.getDebugValues().isEmpty();
      if (instruction.isMoveException()) {
        assert instruction == entry();
        for (BasicBlock pred : getPredecessors()) {
          assert pred.hasCatchSuccessor(this)
              || (pred.isTrivialGoto() && pred.endOfGotoChain() == this);
        }
      }
      if (!instruction.isArgument()) {
        argumentsAllowed = false;
      }
    }
    return true;
  }

  public boolean verifyTypes(AppInfo appInfo) {
    assert instructions.stream().allMatch(instruction -> instruction.verifyTypes(appInfo));
    return true;
  }

  public void setLocalsAtEntry(Int2ReferenceMap<DebugLocalInfo> localsAtEntry) {
    this.localsAtEntry = localsAtEntry;
  }

  public Int2ReferenceMap<DebugLocalInfo> getLocalsAtEntry() {
    return localsAtEntry;
  }

  public void replaceLastInstruction(Instruction instruction) {
    InstructionListIterator iterator = listIterator(getInstructions().size());
    iterator.previous();
    iterator.replaceCurrentInstruction(instruction);
  }

  public enum ThrowingInfo {
    NO_THROW, CAN_THROW
  }

  public enum EdgeType {
    NON_EDGE,
    NORMAL,
    EXCEPTIONAL
  }

  public static class Pair implements Comparable<Pair> {

    public BasicBlock first;
    public BasicBlock second;

    public Pair(BasicBlock first, BasicBlock second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public int compareTo(Pair o) {
      if (first != o.first) {
        return first.getNumber() - o.first.getNumber();
      }
      if (second != o.second) {
        return second.getNumber() - o.second.getNumber();
      }
      return 0;
    }

    @Override
    public String toString() {
      return "Edge: " + first.getNumber() + " -> " + second.getNumber();
    }
  }

  private final List<BasicBlock> successors = new ArrayList<>();
  private final List<BasicBlock> predecessors = new ArrayList<>();

  // Catch handler information about which successors are catch handlers and what their guards are.
  private CatchHandlers<Integer> catchHandlers = CatchHandlers.EMPTY_INDICES;

  private LinkedList<Instruction> instructions = new LinkedList<>();
  private int number = -1;
  private List<Phi> phis = new ArrayList<>();

  // State used during SSA construction. The SSA construction is based on the paper:
  //
  // "Simple and Efficient Construction of Static Single Assignment Form"
  // http://compilers.cs.uni-saarland.de/papers/bbhlmz13cc.pdf
  //
  // A basic block is filled when local value numbering is complete for that block.
  // A basic block is sealed when all predecessor blocks have been filled.
  //
  // Therefore, for a sealed block we can always search backwards to find reaching values
  // in predecessor blocks.
  private boolean filled = false;
  private boolean sealed = false;
  private final Map<Integer, Phi> incompletePhis = new HashMap<>();
  private int estimatedPredecessorsCount = 0;
  private int unfilledPredecessorsCount = 0;

  // State used for basic block sorting and tracing.
  private int color = 0;

  // Map of registers to current SSA value. Used during SSA numbering and cleared once filled.
  private Map<Integer, Value> currentDefinitions = new HashMap<>();

  public List<BasicBlock> getSuccessors() {
    return successors;
  }

  public List<BasicBlock> getNormalSuccessors() {
    if (!hasCatchHandlers()) {
      return successors;
    }
    Set<Integer> handlers = catchHandlers.getUniqueTargets();
    ImmutableList.Builder<BasicBlock> normals = ImmutableList.builder();
    for (int i = 0; i < successors.size(); i++) {
      if (!handlers.contains(i)) {
        normals.add(successors.get(i));
      }
    }
    return normals.build();
  }

  public List<BasicBlock> getPredecessors() {
    return predecessors;
  }

  public List<BasicBlock> getNormalPredecessors() {
    ImmutableList.Builder<BasicBlock> normals = ImmutableList.builder();
    for (BasicBlock predecessor : predecessors) {
      if (!predecessor.hasCatchSuccessor(this)) {
        normals.add(predecessor);
      }
    }
    return normals.build();
  }

  public void removeSuccessor(BasicBlock block) {
    int index = successors.indexOf(block);
    assert index >= 0 : "removeSuccessor did not find the successor to remove";
    removeSuccessorsByIndex(new IntArrayList(new int[] {index}));
  }

  public void removePredecessor(BasicBlock block) {
    int index = predecessors.indexOf(block);
    assert index >= 0 : "removePredecessor did not find the predecessor to remove";
    predecessors.remove(index);
    if (phis != null) {
      for (Phi phi : getPhis()) {
        phi.removeOperand(index);
      }
      // Collect and remove trivial phis after block removal.
      List<Phi> trivials = new ArrayList<>();
      for (Phi phi : getPhis()) {
        if (phi.isTrivialPhi()) {
          trivials.add(phi);
        }
      }
      for (Phi phi : trivials) {
        phi.removeTrivialPhi();
      }
    }
  }

  public void swapSuccessors(BasicBlock a, BasicBlock b) {
    assert a != b;
    int aIndex = successors.indexOf(a);
    int bIndex = successors.indexOf(b);
    assert aIndex >= 0 && bIndex >= 0;
    swapSuccessorsByIndex(aIndex, bIndex);
  }

  public void swapSuccessorsByIndex(int index1, int index2) {
    assert index1 != index2;
    if (hasCatchHandlers()) {
      List<Integer> targets = new ArrayList<>(catchHandlers.getAllTargets());
      assert targets.contains(index1) == targets.contains(index2)
          : "Swapping normal successor and catch handler";
      for (int i = 0; i < targets.size(); i++) {
        if (targets.get(i) == index1) {
          targets.set(i, index2);
        } else if (targets.get(i) == index2) {
          targets.set(i, index1);
        }
      }
      catchHandlers = new CatchHandlers<>(catchHandlers.getGuards(), targets);
    }
    BasicBlock tmp = successors.get(index1);
    successors.set(index1, successors.get(index2));
    successors.set(index2, tmp);
  }

  // TODO(b/116174212): Remove the predecessor pointer from the old successor block.
  public void replaceSuccessor(BasicBlock block, BasicBlock newBlock) {
    assert successors.contains(block) : "attempt to replace non-existent successor";

    if (successors.contains(newBlock)) {
      int indexOfOldBlock = successors.indexOf(block);
      int indexOfNewBlock = successors.indexOf(newBlock);

      // Always rewrite catch handlers.
      if (hasCatchHandlers()) {
        List<Integer> targets = new ArrayList<>(catchHandlers.getAllTargets());
        for (int i = 0; i < targets.size(); i++) {
          if (targets.get(i) == indexOfOldBlock) {
            targets.set(i, indexOfNewBlock);
          }
          if (targets.get(i) > indexOfOldBlock) {
            targets.set(i, targets.get(i) - 1);
          }
        }
        catchHandlers = new CatchHandlers<>(catchHandlers.getGuards(), targets);
      }

      // Check if the replacement influences jump targets and rewrite as needed.
      if (exit().isGoto()) {
        if (indexOfOldBlock == successors.size() - 1 && indexOfNewBlock != successors.size() - 2) {
          // Replacing the goto target and the new block will not become the goto target.
          // We perform a swap to get the new block into the goto target position.
          swapSuccessorsByIndex(indexOfOldBlock - 1, indexOfNewBlock);
        }
      } else if (exit().isIf()) {
        if (indexOfNewBlock >= successors.size() - 2 && indexOfOldBlock >= successors.size() - 2) {
          // New and old are true target and fallthrough, replace last instruction with a goto.
          Instruction instruction = getInstructions().removeLast();
          // Iterate in reverse order to ensure that POP instructions are inserted in correct order.
          for (int i = instruction.inValues().size() - 1; i >= 0; i--) {
            Value value = instruction.inValues().get(i);
            if (value instanceof StackValue) {
              if (value.definition.isLoad()) {
                assert hasLinearFlow(this, value.definition.getBlock());
                value.definition.getBlock().removeInstruction(value.definition);
              } else {
                Pop pop = new Pop((StackValue) value);
                pop.setBlock(this);
                pop.setPosition(instruction.getPosition());
                getInstructions().addLast(pop);
              }
            }
            if (value.hasUsersInfo()) {
              value.removeUser(instruction);
            }
          }
          Instruction exit = new Goto();
          exit.setBlock(this);
          exit.setPosition(instruction.getPosition());
          getInstructions().addLast(exit);
        } else if (indexOfOldBlock >= successors.size() - 2) {
          // Old is either true or fallthrough and we need to swap the new block into the right
          // position to become that target.
          swapSuccessorsByIndex(indexOfOldBlock - 1, indexOfNewBlock);
        }
      } else if (exit().isSwitch()) {
        // Rewrite fallthrough and case target indices.
        Switch exit = exit().asSwitch();
        if (exit.getFallthroughBlockIndex() == indexOfOldBlock) {
          exit.setFallthroughBlockIndex(indexOfNewBlock);
        }
        if (exit.getFallthroughBlockIndex() > indexOfOldBlock) {
          exit.setFallthroughBlockIndex(exit.getFallthroughBlockIndex() - 1);
        }
        int[] indices = exit.targetBlockIndices();
        for (int i = 0; i < indices.length; i++) {
          if (indices[i] == indexOfOldBlock) {
            indices[i] = indexOfNewBlock;
          }
          if (indices[i] > indexOfOldBlock) {
            indices[i] = indices[i] - 1;
          }
        }
      }

      // Remove the replaced successor.
      boolean removed = successors.remove(block);
      assert removed;
    } else {
      // If the new block is not a successor we don't have to rewrite indices or instructions
      // and we can just replace the old successor with the new one.
      for (int i = 0; i < successors.size(); i++) {
        if (successors.get(i) == block) {
          successors.set(i, newBlock);
          return;
        }
      }
    }
  }

  private boolean hasLinearFlow(BasicBlock current, BasicBlock target) {
    while (current != target) {
      if (current.getPredecessors().size() != 1) {
        return false;
      }
      BasicBlock candidate = current.getPredecessors().get(0);
      if (!candidate.exit().isGoto() || candidate.exit().asGoto().getTarget() != current) {
        return false;
      }
      current = candidate;
    }
    return true;
  }

  public void replacePredecessor(BasicBlock block, BasicBlock newBlock) {
    for (int i = 0; i < predecessors.size(); i++) {
      if (predecessors.get(i) == block) {
        predecessors.set(i, newBlock);
        return;
      }
    }
    assert false : "replaceSuccessor did not find the predecessor to replace";
  }

  public void removeSuccessorsByIndex(IntList successorsToRemove) {
    if (successorsToRemove.isEmpty()) {
      return;
    }
    assert ListUtils.verifyListIsOrdered(successorsToRemove);
    List<BasicBlock> copy = new ArrayList<>(successors);
    successors.clear();
    int current = 0;
    for (int i : successorsToRemove) {
      successors.addAll(copy.subList(current, i));
      current = i + 1;
    }
    successors.addAll(copy.subList(current, copy.size()));

    if (hasCatchHandlers()) {
      List<Integer> currentTargets = catchHandlers.getAllTargets();
      List<DexType> currentGuards = catchHandlers.getGuards();
      int size = catchHandlers.size();
      List<DexType> newGuards = new ArrayList<>(size);
      List<Integer> newTargets = new ArrayList<>(size);

      // Since targets represent indices in the list of successors, we
      // need to remove targets/indices included in successorsToRemove,
      // and decrease the rest of targets/indices to reflect removed successors.
      outer:
      for (int i = 0; i < currentTargets.size(); i++) {
        int index = currentTargets.get(i);
        int decreaseBy = 0;
        for (int removedIndex : successorsToRemove) {
          if (index == removedIndex) {
            continue outer; // target was removed
          }
          if (index < removedIndex) {
            break;
          }
          decreaseBy++;
        }
        newTargets.add(index - decreaseBy);
        newGuards.add(currentGuards.get(i));
      }

      if (newTargets.isEmpty()) {
        catchHandlers = CatchHandlers.EMPTY_INDICES;
      } else {
        catchHandlers = new CatchHandlers<>(newGuards, newTargets);
      }
    }
  }

  public void removePredecessorsByIndex(List<Integer> predecessorsToRemove) {
    if (predecessorsToRemove.isEmpty()) {
      return;
    }
    List<BasicBlock> copy = new ArrayList<>(predecessors);
    predecessors.clear();
    int current = 0;
    for (int i : predecessorsToRemove) {
      predecessors.addAll(copy.subList(current, i));
      current = i + 1;
    }
    predecessors.addAll(copy.subList(current, copy.size()));
  }

  public void removePhisByIndex(List<Integer> predecessorsToRemove) {
    for (Phi phi : phis) {
      phi.removeOperandsByIndex(predecessorsToRemove);
    }
  }

  public List<Phi> getPhis() {
    return phis;
  }

  public boolean isFilled() {
    return filled;
  }

  public void setFilledForTesting() {
    filled = true;
  }

  public boolean hasCatchHandlers() {
    assert catchHandlers != null;
    return !catchHandlers.isEmpty();
  }

  public int getNumber() {
    assert number >= 0;
    return number;
  }

  public void setNumber(int number) {
    assert number >= 0;
    this.number = number;
  }

  public String getNumberAsString() {
    return number >= 0 ? "" + number : "<unknown>";
  }

  public int numberInstructions(int nextInstructionNumber) {
    for (Instruction instruction : instructions) {
      instruction.setNumber(nextInstructionNumber);
      nextInstructionNumber += INSTRUCTION_NUMBER_DELTA;
    }
    return nextInstructionNumber;
  }

  public LinkedList<Instruction> getInstructions() {
    return instructions;
  }

  public boolean isEmpty() {
    return instructions.isEmpty();
  }

  public Instruction entry() {
    return instructions.get(0);
  }

  public JumpInstruction exit() {
    assert filled;
    assert instructions.get(instructions.size() - 1).isJumpInstruction();
    return instructions.get(instructions.size() - 1).asJumpInstruction();
  }

  public Instruction exceptionalExit() {
    assert hasCatchHandlers();
    ListIterator<Instruction> it = listIterator(instructions.size());
    while (it.hasPrevious()) {
      Instruction instruction = it.previous();
      if (instruction.instructionTypeCanThrow()) {
        return instruction;
      }
    }
    return null;
  }

  public void clearUserInfo() {
    phis = null;
    instructions.forEach(Instruction::clearUserInfo);
  }

  public void buildDex(DexBuilder builder) {
    for (Instruction instruction : instructions) {
      instruction.buildDex(builder);
    }
  }

  public void mark(int color) {
    assert color != 0;
    assert !isMarked(color);
    this.color |= color;
    assert isMarked(color);
  }

  public void clearMark(int color) {
    assert color != 0;
    this.color &= ~color;
    assert !isMarked(color);
  }

  public boolean isMarked(int color) {
    assert color != 0;
    return (this.color & color) != 0;
  }

  public void incrementUnfilledPredecessorCount() {
    ++unfilledPredecessorsCount;
    ++estimatedPredecessorsCount;
  }

  public void decrementUnfilledPredecessorCount(int n) {
    unfilledPredecessorsCount -= n;
    estimatedPredecessorsCount -= n;
  }

  public void decrementUnfilledPredecessorCount() {
    --unfilledPredecessorsCount;
    --estimatedPredecessorsCount;
  }

  public boolean verifyFilledPredecessors() {
    assert estimatedPredecessorsCount == predecessors.size();
    assert unfilledPredecessorsCount == 0;
    return true;
  }

  public void addPhi(Phi phi) {
    phis.add(phi);
  }

  public void removePhi(Phi phi) {
    phis.remove(phi);
    assert currentDefinitions == null || !currentDefinitions.containsValue(phi)
        : "Attempt to remove Phi " + phi + " which is present in currentDefinitions";
  }

  public void add(Instruction next) {
    assert !isFilled();
    instructions.add(next);
    next.setBlock(this);
  }

  public void close(IRBuilder builder) {
    assert !isFilled();
    assert !instructions.isEmpty();
    filled = true;
    sealed = unfilledPredecessorsCount == 0;
    assert exit().isJumpInstruction();
    assert verifyNoValuesAfterThrowingInstruction();
    for (BasicBlock successor : successors) {
      successor.filledPredecessor(builder);
    }
  }

  public void link(BasicBlock successor) {
    assert !successors.contains(successor);
    assert !successor.predecessors.contains(this);
    successors.add(successor);
    successor.predecessors.add(this);
  }

  private static boolean allPredecessorsDominated(BasicBlock block, DominatorTree dominator) {
    for (BasicBlock pred : block.predecessors) {
      if (!dominator.dominatedBy(pred, block)) {
        return false;
      }
    }
    return true;
  }

  private static boolean blocksClean(List<BasicBlock> blocks) {
    blocks.forEach((b) -> {
      assert b.predecessors.size() == 0;
      assert b.successors.size() == 0;
    });
    return true;
  }

  /**
   * Unlinks this block from a single predecessor.
   *
   * @return returns the unlinked predecessor.
   */
  public BasicBlock unlinkSinglePredecessor() {
    assert predecessors.size() == 1;
    assert predecessors.get(0).successors.size() == 1;
    BasicBlock unlinkedBlock = predecessors.get(0);
    unlinkedBlock.successors.clear();
    predecessors.clear();
    return unlinkedBlock;
  }

  /** Like unlinkSinglePredecessor but the predecessor may have multiple successors. */
  public void unlinkSinglePredecessorSiblingsAllowed() {
    assert predecessors.size() == 1; // There are no critical edges.
    assert predecessors.get(0).successors.contains(this);
    predecessors.get(0).successors.remove(this);
    predecessors.clear();
  }

  /**
   * Unlinks this block from a single normal successor.
   *
   * @return Returns the unlinked successor.
   */
  public BasicBlock unlinkSingleSuccessor() {
    assert !hasCatchHandlers();
    assert successors.size() == 1;
    assert successors.get(0).predecessors.size() == 1;
    BasicBlock unlinkedBlock = successors.get(0);
    unlinkedBlock.predecessors.clear();
    successors.clear();
    return unlinkedBlock;
  }

  /**
   * Unlinks the current block based on the assumption that it is a catch handler.
   *
   * Catch handlers always have only one predecessor and at most one successor.
   * That is because we have edge-split form for all exceptional flow.
   */
  public void unlinkCatchHandler() {
    assert predecessors.size() == 1;
    predecessors.get(0).removeSuccessor(this);
    predecessors.clear();
  }

  public void detachAllSuccessors() {
    for (BasicBlock successor : successors) {
      successor.predecessors.remove(this);
    }
    successors.clear();
  }

  public List<BasicBlock> unlink(BasicBlock successor, DominatorTree dominator) {
    assert successors.contains(successor);
    assert successor.predecessors.size() == 1; // There are no critical edges.
    assert successor.predecessors.get(0) == this;
    List<BasicBlock> removedBlocks = new ArrayList<>();
    for (BasicBlock dominated : dominator.dominatedBlocks(successor)) {
      dominated.cleanForRemoval();
      removedBlocks.add(dominated);
    }
    assert blocksClean(removedBlocks);
    return removedBlocks;
  }

  public void cleanForRemoval() {
    for (BasicBlock block : successors) {
      block.removePredecessor(this);
    }
    successors.clear();
    for (BasicBlock block : predecessors) {
      block.removeSuccessor(this);
    }
    predecessors.clear();
    for (Phi phi : getPhis()) {
      for (Value operand : phi.getOperands()) {
        operand.removePhiUser(phi);
      }
    }
    getPhis().clear();
    for (Instruction instruction : getInstructions()) {
      if (instruction.outValue != null) {
        instruction.outValue.clearUsers();
        instruction.setOutValue(null);
      }
      for (Value value : instruction.inValues) {
        value.removeUser(instruction);
      }
      for (Value value : instruction.getDebugValues()) {
        value.removeDebugUser(instruction);
      }
    }
  }

  public void linkCatchSuccessors(List<DexType> guards, List<BasicBlock> targets) {
    List<Integer> successorIndexes = new ArrayList<>(targets.size());
    for (BasicBlock target : targets) {
      int index = successors.indexOf(target);
      if (index < 0) {
        index = successors.size();
        link(target);
      }
      successorIndexes.add(index);
    }
    catchHandlers = new CatchHandlers<>(guards, successorIndexes);
  }

  public void addCatchHandler(BasicBlock rethrowBlock, DexType guard) {
    assert !hasCatchHandlers();
    successors.add(0, rethrowBlock);
    rethrowBlock.getPredecessors().add(this);
    catchHandlers = new CatchHandlers<>(ImmutableList.of(guard), ImmutableList.of(0));
  }

  // Due to class merging, it is possible that two exception classes have been merged into one.
  // This function renames the guards according to the given graph lense.
  public void renameGuardsInCatchHandlers(GraphLense graphLense) {
    assert hasCatchHandlers();
    List<DexType> newGuards = new ArrayList<>(catchHandlers.getGuards().size());
    for (DexType guard : catchHandlers.getGuards()) {
      // The type may have changed due to class merging.
      newGuards.add(graphLense.lookupType(guard));
    }
    this.catchHandlers = new CatchHandlers<>(newGuards, catchHandlers.getAllTargets());
  }

  public boolean consistentCatchHandlers() {
    // Check that catch handlers are always the first successors of a block.
    if (hasCatchHandlers()) {
      assert exit().isGoto() || exit().isThrow();
      CatchHandlers<Integer> catchHandlers = getCatchHandlersWithSuccessorIndexes();
      // Check that guards are unique.
      assert catchHandlers.getGuards().size()
          == ImmutableSet.copyOf(catchHandlers.getGuards()).size();
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
      int lastSuccessorIndex = getSuccessors().size() - 1;
      assert lastIndex == lastSuccessorIndex // All successors are catch successors.
          || lastIndex == lastSuccessorIndex - 1; // All but one successors are catch successors.
      assert lastIndex == lastSuccessorIndex || !exit().isThrow();
    }
    return true;
  }

  public void clearCurrentDefinitions() {
    currentDefinitions = null;
    for (Phi phi : getPhis()) {
      phi.clearDefinitionsUsers();
    }
  }

  // The proper incoming register for a catch successor (that is otherwise shadowed by the out-value
  // of a throwing instruction) is stored at the negative register-index in the definitions map.
  // (See readCurrentDefinition/writeCurrentDefinition/updateCurrentDefinition).
  private static int onThrowValueRegister(int register) {
    return -(register + 1);
  }

  private Value readOnThrowValue(int register, EdgeType readingEdge) {
    if (readingEdge == EdgeType.EXCEPTIONAL) {
      return currentDefinitions.get(onThrowValueRegister(register));
    }
    return null;
  }

  private boolean isOnThrowValue(int register, EdgeType readingEdge) {
    return readOnThrowValue(register, readingEdge) != null;
  }

  public Value readCurrentDefinition(int register, EdgeType readingEdge) {
    // If the block reading the current definition is a catch successor, then we must return the
    // previous value of the throwing-instructions outgoing register if any.
    Value result = readOnThrowValue(register, readingEdge);
    if (result != null) {
      return result == Value.UNDEFINED ? null : result;
    }
    return currentDefinitions.get(register);
  }

  public void replaceCurrentDefinitions(Value oldValue, Value newValue) {
    assert oldValue.definition.getBlock() == this;
    assert !oldValue.isUsed();
    for (Entry<Integer, Value> entry : currentDefinitions.entrySet()) {
      if (entry.getValue() == oldValue) {
        if (oldValue.isPhi()) {
          oldValue.asPhi().removeDefinitionsUser(currentDefinitions);
        }
        entry.setValue(newValue);
        if (newValue.isPhi()) {
          newValue.asPhi().addDefinitionsUser(currentDefinitions);
        }
      }
    }
  }

  public void updateCurrentDefinition(int register, Value value, EdgeType readingEdge) {
    // If the reading/writing block is a catch successor, possibly update the on-throw value.
    if (isOnThrowValue(register, readingEdge)) {
      register = onThrowValueRegister(register);
    }
    // We keep track of all users of phis so that we can update all users during
    // trivial phi elimination. We only rewrite phi values during IR construction, so
    // we only need to record definition users for phis.
    Value previousValue = currentDefinitions.get(register);
    if (value.isPhi()) {
      value.asPhi().addDefinitionsUser(currentDefinitions);
    }
    assert verifyOnThrowWrite(register);
    currentDefinitions.put(register, value);
    // We have replaced one occurrence of value in currentDefinitions. There could be
    // other occurrences. We only remove currentDefinitions from the set of users
    // of the phi if we have removed all occurrences.
    if (previousValue != null &&
        previousValue.isPhi() &&
        !currentDefinitions.values().contains(previousValue)) {
      previousValue.asPhi().removeDefinitionsUser(currentDefinitions);
    }
  }

  public void writeCurrentDefinition(int register, Value value, ThrowingInfo throwing) {
    // If this write is dependent on not throwing, we move the existing value to its negative index
    // so that it can be read by catch successors.
    if (throwing == ThrowingInfo.CAN_THROW) {
      Value previous = currentDefinitions.get(register);
      assert verifyOnThrowWrite(register);
      currentDefinitions.put(onThrowValueRegister(register),
          previous == null ? Value.UNDEFINED : previous);
    }
    updateCurrentDefinition(register, value, EdgeType.NON_EDGE);
  }

  public void filledPredecessor(IRBuilder builder) {
    assert unfilledPredecessorsCount > 0;
    if (--unfilledPredecessorsCount == 0) {
      assert estimatedPredecessorsCount == predecessors.size();
      for (Entry<Integer, Phi> entry : incompletePhis.entrySet()) {
        int register = entry.getKey();
        if (register < 0) {
          register = onThrowValueRegister(register);
        }
        entry.getValue().addOperands(builder, register);
      }
      sealed = true;
      incompletePhis.clear();
    }
  }

  public EdgeType getEdgeType(BasicBlock successor) {
    assert successors.indexOf(successor) >= 0;
    return hasCatchSuccessor(successor) ? EdgeType.EXCEPTIONAL : EdgeType.NORMAL;
  }

  public boolean hasCatchSuccessor(BasicBlock block) {
    if (!hasCatchHandlers()) {
      return false;
    }
    return catchHandlers.getUniqueTargets().contains(successors.indexOf(block));
  }

  public int guardsForCatchSuccessor(BasicBlock block) {
    assert hasCatchSuccessor(block);
    int index = successors.indexOf(block);
    int count = 0;
    for (int handler : catchHandlers.getAllTargets()) {
      if (handler == index) {
        count++;
      }
    }
    assert count > 0;
    return count;
  }

  public boolean isSealed() {
    return sealed;
  }

  public void addIncompletePhi(int register, Phi phi, EdgeType readingEdge) {
    if (isOnThrowValue(register, readingEdge)) {
      register = onThrowValueRegister(register);
    }
    assert !incompletePhis.containsKey(register);
    incompletePhis.put(register, phi);
  }

  public boolean hasIncompletePhis() {
    return !incompletePhis.isEmpty();
  }

  public Collection<Integer> getIncompletePhiRegisters() {
    return incompletePhis.keySet();
  }

  private static void appendBasicBlockList(
      StringBuilder builder, List<BasicBlock> list, Function<BasicBlock, String> postfix) {
    if (list.size() > 0) {
      for (BasicBlock block : list) {
        builder.append(block.getNumberAsString());
        builder.append(postfix.apply(block));
        builder.append(' ');
      }
    } else {
      builder.append('-');
    }
  }

  @Override
  public String toString() {
    return toDetailedString();
  }

  public String toSimpleString() {
    return number < 0 ? super.toString() : ("block " + number);
  }

  private String predecessorPostfix(BasicBlock block) {
    if (hasCatchSuccessor(block)) {
      return new String(new char[guardsForCatchSuccessor(block)]).replace("\0", "*");
    }
    return "";
  }

  private static int digits(int number) {
    return (int) Math.ceil(Math.log10(number + 1));
  }

  public String toDetailedString() {
    StringBuilder builder = new StringBuilder();
    builder.append("block ");
    builder.append(number);
    builder.append(", pred-counts: " + predecessors.size());
    if (unfilledPredecessorsCount > 0) {
      builder.append(" (" + unfilledPredecessorsCount + " unfilled)");
    }
    builder.append(", succ-count: " + successors.size());
    builder.append(", filled: " + isFilled());
    builder.append(", sealed: " + isSealed());
    builder.append('\n');
    builder.append("predecessors: ");
    appendBasicBlockList(builder, predecessors, b -> "");
    builder.append('\n');
    builder.append("successors: ");
    appendBasicBlockList(builder, successors, this::predecessorPostfix);
    if (successors.size() > 0) {
      builder.append(" (");
      if (hasCatchHandlers()) {
        builder.append(catchHandlers.size());
      } else {
        builder.append("no");
      }
      builder.append(" try/catch successors)");
    }
    builder.append('\n');
    if (phis != null && phis.size() > 0) {
      for (Phi phi : phis) {
        builder.append(phi.printPhi());
        if (incompletePhis.values().contains(phi)) {
          builder.append(" (incomplete)");
        }
        builder.append('\n');
      }
    } else {
      builder.append("no phis\n");
    }
    if (localsAtEntry != null) {
      builder.append("locals: ");
      StringUtils.append(builder, localsAtEntry.int2ReferenceEntrySet(), ", ", BraceType.NONE);
      builder.append('\n');
    }
    int lineColumn = 0;
    int numberColumn = 0;
    for (Instruction instruction : instructions) {
      lineColumn = Math.max(lineColumn, instruction.getPositionAsString().length());
      numberColumn = Math.max(numberColumn, digits(instruction.getNumber()));
    }
    String currentPosition = null;
    for (Instruction instruction : instructions) {
      if (lineColumn > 0) {
        String line = "";
        if (!instruction.getPositionAsString().equals(currentPosition)) {
          line = currentPosition = instruction.getPositionAsString();
        }
        StringUtils.appendLeftPadded(builder, line, lineColumn + 1);
        builder.append(": ");
      }
      StringUtils.appendLeftPadded(builder, "" + instruction.getNumber(), numberColumn + 1);
      builder.append(": ");
      builder.append(instruction.toString());
      if (DebugLocalInfo.PRINT_LEVEL != PrintLevel.NONE) {
        List<Value> localEnds = new ArrayList<>(instruction.getDebugValues().size());
        List<Value> localStarts = new ArrayList<>(instruction.getDebugValues().size());
        List<Value> localLive = new ArrayList<>(instruction.getDebugValues().size());
        for (Value value : instruction.getDebugValues()) {
          if (value.getDebugLocalEnds().contains(instruction)) {
            localEnds.add(value);
          } else if (value.getDebugLocalStarts().contains(instruction)) {
            localStarts.add(value);
          } else {
            assert value.debugUsers().contains(instruction);
            localLive.add(value);
          }
        }
        printDebugValueSet("live", localLive, builder);
        printDebugValueSet("end", localEnds, builder);
        printDebugValueSet("start", localStarts, builder);
      }
      builder.append("\n");
    }
    return builder.toString();
  }

  private void printDebugValueSet(String header, List<Value> locals, StringBuilder builder) {
    if (!locals.isEmpty()) {
      builder.append(" [").append(header).append(": ");
      StringUtils.append(builder, locals, ", ", BraceType.NONE);
      builder.append("]");
    }
  }

  public void print(CfgPrinter printer) {
    printer.begin("block");
    printer.print("name \"B").append(number).append("\"\n");
    printer.print("from_bci -1\n");
    printer.print("to_bci -1\n");
    printer.print("predecessors");
    printBlockList(printer, predecessors);
    printer.ln();
    printer.print("successors");
    printBlockList(printer, successors);
    printer.ln();
    printer.print("xhandlers\n");
    printer.print("flags\n");
    printer.print("first_lir_id ").print(instructions.get(0).getNumber()).ln();
    printer.print("last_lir_id ").print(instructions.get(instructions.size() - 1).getNumber()).ln();
    printer.begin("HIR");
    if (phis != null) {
      for (Phi phi : phis) {
        phi.print(printer);
        printer.append(" <|@\n");
      }
    }
    for (Instruction instruction : instructions) {
      instruction.print(printer);
      printer.append(" <|@\n");
    }
    printer.end("HIR");
    printer.begin("LIR");
    for (Instruction instruction : instructions) {
      instruction.printLIR(printer);
      printer.append(" <|@\n");
    }
    printer.end("LIR");
    printer.end("block");
  }

  private static void printBlockList(CfgPrinter printer, List<BasicBlock> blocks) {
    for (BasicBlock block : blocks) {
      printer.append(" \"B").append(block.number).append("\"");
    }
  }

  public void addPhiMove(Move move) {
    // TODO(ager): Consider this more, is it always the case that we should add it before the
    // exit instruction?
    Instruction branch = exit();
    instructions.set(instructions.size() - 1, move);
    instructions.add(branch);
  }

  public void setInstructions(LinkedList<Instruction> instructions) {
    this.instructions = instructions;
  }

  /**
   * Remove a number of instructions. The instructions to remove are given as indexes in the
   * instruction stream.
   */
  public void removeInstructions(List<Integer> toRemove) {
    if (!toRemove.isEmpty()) {
      LinkedList<Instruction> newInstructions = new LinkedList<>();
      int nextIndex = 0;
      for (Integer index : toRemove) {
        assert index >= nextIndex;  // Indexes in toRemove must be sorted ascending.
        newInstructions.addAll(instructions.subList(nextIndex, index));
        instructions.get(index).clearBlock();
        nextIndex = index + 1;
      }
      if (nextIndex < instructions.size()) {
        newInstructions.addAll(instructions.subList(nextIndex, instructions.size()));
      }
      assert instructions.size() == newInstructions.size() + toRemove.size();
      setInstructions(newInstructions);
    }
  }

  /**
   * Remove an instruction.
   */
  public void removeInstruction(Instruction toRemove) {
    int index = instructions.indexOf(toRemove);
    assert index >= 0;
    removeInstructions(Collections.singletonList(index));
  }

  /**
   * Create a new basic block with a single goto instruction.
   *
   * <p>The constructed basic block has no predecessors and has one successor which is the target
   * block.
   *
   * @param blockNumber the block number of the goto block
   * @param target the target of the goto block
   */
  public static BasicBlock createGotoBlock(int blockNumber, Position position, BasicBlock target) {
    BasicBlock block = createGotoBlock(blockNumber, position);
    block.getSuccessors().add(target);
    return block;
  }

  /**
   * Create a new basic block with a single goto instruction.
   *
   * <p>The constructed basic block has no predecessors and no successors.
   *
   * @param blockNumber the block number of the goto block
   */
  public static BasicBlock createGotoBlock(int blockNumber, Position position) {
    BasicBlock block = new BasicBlock();
    block.add(new Goto());
    block.close(null);
    block.setNumber(blockNumber);
    block.entry().setPosition(position);
    return block;
  }

  /**
   * Create a new basic block with a single if instruction.
   *
   * <p>The constructed basic block has no predecessors and no successors.
   *
   * @param blockNumber the block number of the block
   * @param theIf the if instruction
   */
  public static BasicBlock createIfBlock(int blockNumber, If theIf) {
    BasicBlock block = new BasicBlock();
    block.add(theIf);
    block.close(null);
    block.setNumber(blockNumber);
    return block;
  }

  /**
   * Create a new basic block with an instruction followed by an if instruction.
   *
   * <p>The constructed basic block has no predecessors and no successors.
   *
   * @param blockNumber the block number of the block
   * @param theIf the if instruction
   * @param instruction the instruction to place before the if instruction
   */
  public static BasicBlock createIfBlock(int blockNumber, If theIf, Instruction instruction) {
    BasicBlock block = new BasicBlock();
    block.add(instruction);
    block.add(theIf);
    block.close(null);
    block.setNumber(blockNumber);
    return block;
  }

  public static BasicBlock createSwitchBlock(int blockNumber, Switch theSwitch) {
    BasicBlock block = new BasicBlock();
    block.add(theSwitch);
    block.close(null);
    block.setNumber(blockNumber);
    return block;
  }

  public static BasicBlock createRethrowBlock(
      IRCode code, Position position, TypeLatticeElement guardTypeLattice) {
    BasicBlock block = new BasicBlock();
    MoveException moveException = new MoveException(
        new Value(code.valueNumberGenerator.next(), guardTypeLattice, null));
    moveException.setPosition(position);
    Throw throwInstruction = new Throw(moveException.outValue);
    throwInstruction.setPosition(position);
    block.add(moveException);
    block.add(throwInstruction);
    block.close(null);
    block.setNumber(code.getHighestBlockNumber() + 1);
    return block;
  }

  public boolean isTrivialGoto() {
    return instructions.size() == 1 && exit().isGoto();
  }

  // Find the final target from this goto block. Returns null if the goto chain is cyclic.
  public BasicBlock endOfGotoChain() {
    // See Floyd's cycle-finding algorithm for reference.
    BasicBlock hare = this;
    BasicBlock tortuous = this;
    boolean advance = false;
    while (hare.isTrivialGoto()) {
      hare = hare.exit().asGoto().getTarget();
      tortuous = advance ? tortuous.exit().asGoto().getTarget() : tortuous;
      advance = !advance;
      if (hare == tortuous) {
        return null;
      }
    }
    return hare;
  }

  public boolean isSimpleAlwaysThrowingPath() {
    // See Floyd's cycle-finding algorithm for reference.
    BasicBlock hare = this;
    BasicBlock tortuous = this;
    boolean advance = false;
    while (true) {
      List<BasicBlock> normalSuccessors = hare.getNormalSuccessors();
      if (normalSuccessors.size() > 1) {
        return false;
      }

      if (normalSuccessors.size() == 0) {
        return hare.exit().isThrow();
      }

      hare = normalSuccessors.get(0);
      tortuous = advance ? tortuous.getNormalSuccessors().get(0) : tortuous;
      advance = !advance;
      if (hare == tortuous) {
        return false;
      }
    }
  }

  public Position getPosition() {
    return entry().getPosition();
  }

  public boolean hasOneNormalExit() {
    return successors.size() == 1 && exit().isGoto();
  }

  public CatchHandlers<BasicBlock> getCatchHandlers() {
    if (!hasCatchHandlers()) {
      return CatchHandlers.EMPTY_BASIC_BLOCK;
    }
    List<BasicBlock> targets = ListUtils.map(catchHandlers.getAllTargets(), successors::get);
    return new CatchHandlers<>(catchHandlers.getGuards(), targets);
  }

  public CatchHandlers<Integer> getCatchHandlersWithSuccessorIndexes() {
    return catchHandlers;
  }

  public void clearCatchHandlers() {
    catchHandlers = CatchHandlers.EMPTY_INDICES;
  }

  public void transferCatchHandlers(BasicBlock other) {
    catchHandlers = other.catchHandlers;
    other.catchHandlers = CatchHandlers.EMPTY_INDICES;
  }

  public int numberOfCatchHandlers() {
    return catchHandlers.size();
  }

  public int numberOfThrowingInstructions() {
    int count = 0;
    for (Instruction instruction : getInstructions()) {
      if (instruction.instructionTypeCanThrow()) {
        count++;
      }
    }
    return count;
  }

  public boolean canThrow() {
    for (Instruction instruction : instructions) {
      if (instruction.instructionTypeCanThrow()) {
        return true;
      }
    }
    return false;
  }

  // A block can have at most one "on throw" value.
  private boolean verifyOnThrowWrite(int register) {
    if (register >= 0) {
      return true;
    }
    for (Integer other : currentDefinitions.keySet()) {
      assert other >= 0 || other == register;
    }
    return true;
  }

  // Verify that if this block has a throwing instruction none of the following instructions may
  // introduce an SSA value. Such values can lead to invalid uses since the values are not actually
  // visible to exceptional successors.
  private boolean verifyNoValuesAfterThrowingInstruction() {
    if (hasCatchHandlers()) {
      ListIterator<Instruction> iterator = listIterator(instructions.size());
      while (iterator.hasPrevious()) {
        Instruction instruction = iterator.previous();
        if (instruction.instructionTypeCanThrow()) {
          return true;
        }
        assert instruction.outValue() == null;
      }
    }
    return true;
  }

  public InstructionIterator iterator() {
    return new BasicBlockInstructionIterator(this);
  }

  public InstructionListIterator listIterator() {
    return new BasicBlockInstructionIterator(this);
  }

  public InstructionListIterator listIterator(int index) {
    return new BasicBlockInstructionIterator(this, index);
  }

  /**
   * Creates an instruction list iterator starting at <code>instruction</code>.
   *
   * The cursor will be positioned after <code>instruction</code>. Calling <code>next</code> on
   * the returned iterator will return the instruction after <code>instruction</code>. Calling
   * <code>previous</code> will return <code>instruction</code>.
   */
  public InstructionListIterator listIterator(Instruction instruction) {
    return new BasicBlockInstructionIterator(this, instruction);
  }

  /**
   * Creates a new empty block as a successor for this block.
   *
   * The new block will have all the normal successors of the original block.
   *
   * The catch successors are either on the original block or the new block depending on the
   * value of <code>keepCatchHandlers</code>.
   *
   * The current block still has all the instructions, and the new block is empty instruction-wise.
   *
   * @param blockNumber block number for new block
   * @param keepCatchHandlers keep catch successors on the original block
   * @return the new block
   */
  BasicBlock createSplitBlock(int blockNumber, boolean keepCatchHandlers) {
    boolean hadCatchHandlers = hasCatchHandlers();
    BasicBlock newBlock = new BasicBlock();
    newBlock.setNumber(blockNumber);

    // Copy all successors including catch handlers to the new block, and update predecessors.
    newBlock.successors.addAll(successors);
    for (BasicBlock successor : newBlock.getSuccessors()) {
      successor.replacePredecessor(this, newBlock);
    }
    successors.clear();
    newBlock.catchHandlers = catchHandlers;
    catchHandlers = CatchHandlers.EMPTY_INDICES;

    // If the catch handlers should be kept on the original block move them back.
    if (keepCatchHandlers && hadCatchHandlers) {
      moveCatchHandlers(newBlock);
    }

    // Link the two blocks
    link(newBlock);

    // Mark the new block filled and sealed.
    newBlock.filled = true;
    newBlock.sealed = true;

    return newBlock;
  }

  /**
   * Moves catch successors from `fromBlock` into this block.
   */
  public void moveCatchHandlers(BasicBlock fromBlock) {
    List<BasicBlock> catchSuccessors = appendCatchHandlers(fromBlock);
    for (BasicBlock successor : catchSuccessors) {
      fromBlock.successors.remove(successor);
      successor.removePredecessor(fromBlock);
    }
    fromBlock.catchHandlers = CatchHandlers.EMPTY_INDICES;
  }

  /**
   * Clone catch successors from `fromBlock` into this block.
   */
  public void copyCatchHandlers(
      IRCode code, ListIterator<BasicBlock> blockIterator, BasicBlock fromBlock) {
    if (catchHandlers != null && catchHandlers.hasCatchAll()) {
      return;
    }
    List<BasicBlock> catchSuccessors = appendCatchHandlers(fromBlock);

    // After cloning is done all catch handler targets are referenced from both the
    // original and the newly created catch handlers. Thus, since we keep both of
    // them, we need to split appropriate edges to make sure every catch handler
    // target block has only one predecessor.
    //
    // Note that for each catch handler block target block we actually create two new blocks:
    // a copy of the original block and a new block to serve as a merging point for
    // the original and its copy. This actually simplifies things since we only need
    // one new phi to merge the two exception values, and all other phis don't need
    // to be changed.
    for (BasicBlock catchSuccessor : catchSuccessors) {
      catchSuccessor.splitCriticalExceptionEdges(
          code.getHighestBlockNumber() + 1,
          code.valueNumberGenerator,
          blockIterator::add);
    }
  }

  /**
   * Assumes that `this` block is a catch handler target (note that it does not have to
   * start with MoveException instruction, since the instruction can be removed by
   * optimizations like dead code remover.
   *
   * Introduces new blocks on all incoming edges and clones MoveException instruction to
   * these blocks if it exists. All exception values introduced in newly created blocks
   * are combined in a phi added to `this` block.
   *
   * Note that if there are any other phis defined on this block, they remain valid, since
   * this method does not affect incoming edges in any way, and just adds new blocks with
   * MoveException and Goto.
   */
  public int splitCriticalExceptionEdges(
      int nextBlockNumber,
      ValueNumberGenerator valueNumberGenerator,
      Consumer<BasicBlock> onNewBlock) {
    List<BasicBlock> predecessors = this.getPredecessors();
    boolean hasMoveException = entry().isMoveException();
    TypeLatticeElement exceptionTypeLattice = null;
    MoveException move = null;
    Position position = entry().getPosition();
    if (hasMoveException) {
      // Remove the move-exception instruction.
      move = entry().asMoveException();
      exceptionTypeLattice = move.outValue().getTypeLattice();
      assert move.getDebugValues().isEmpty();
      getInstructions().remove(0);
    }
    // Create new predecessor blocks.
    List<BasicBlock> newPredecessors = new ArrayList<>();
    List<Value> values = new ArrayList<>(predecessors.size());
    for (BasicBlock predecessor : predecessors) {
      if (!predecessor.hasCatchSuccessor(this)) {
        throw new CompilationError(
            "Invalid block structure: catch block reachable via non-exceptional flow.");
      }
      BasicBlock newBlock = new BasicBlock();
      newBlock.setNumber(nextBlockNumber++);
      newPredecessors.add(newBlock);
      if (hasMoveException) {
        Value value = new Value(
            valueNumberGenerator.next(),
            exceptionTypeLattice,
            move.getLocalInfo());
        values.add(value);
        MoveException newMove = new MoveException(value);
        newBlock.add(newMove);
        newMove.setPosition(position);
      }
      Goto next = new Goto();
      next.setPosition(position);
      newBlock.add(next);
      newBlock.close(null);
      newBlock.getSuccessors().add(this);
      newBlock.getPredecessors().add(predecessor);
      predecessor.replaceSuccessor(this, newBlock);
      onNewBlock.accept(newBlock);
      assert newBlock.getNumber() >= 0 : "Number must be assigned by `onNewBlock`";
    }
    // Replace the blocks predecessors with the new ones.
    predecessors.clear();
    predecessors.addAll(newPredecessors);
    // Insert a phi for the move-exception value.
    if (hasMoveException) {
      Phi phi =
          new Phi(
              valueNumberGenerator.next(),
              this,
              exceptionTypeLattice,
              move.getLocalInfo(),
              RegisterReadType.NORMAL);
      phi.addOperands(values);
      move.outValue().replaceUsers(phi);
    }
    return nextBlockNumber;
  }

  /**
   * Append catch handlers from another block <code>fromBlock</code> (which must have catch
   * handlers) to the catch handlers of this block.
   *
   * Note that after appending catch handlers their targets are referenced by both
   * <code>fromBlock</code> and <code>this</code> block, but no phis are inserted. For this reason
   * this method should only be called from either {@link #moveCatchHandlers} or
   * {@link #copyCatchHandlers} which know how to handle phis.
   *
   * @return the catch successors that are reused in both blocks after appending.
   */
  private List<BasicBlock> appendCatchHandlers(BasicBlock fromBlock) {
    assert fromBlock.hasCatchHandlers();

    List<Integer> prevCatchTargets = fromBlock.catchHandlers.getAllTargets();
    List<DexType> prevCatchGuards = fromBlock.catchHandlers.getGuards();
    List<BasicBlock> catchSuccessors = new ArrayList<>();
    List<DexType> newCatchGuards = new ArrayList<>();
    List<Integer> newCatchTargets = new ArrayList<>();

    // First add existing catch handlers to the catch handler list.
    if (hasCatchHandlers()) {
      newCatchGuards.addAll(catchHandlers.getGuards());
      newCatchTargets.addAll(catchHandlers.getAllTargets());
      for (int newCatchTarget : newCatchTargets) {
        BasicBlock catchSuccessor = successors.get(newCatchTarget);
        if (!catchSuccessors.contains(catchSuccessor)) {
          catchSuccessors.add(catchSuccessor);
        }
        int index = catchSuccessors.indexOf(catchSuccessor);
        assert index == newCatchTarget;
      }
    }

    // This is the number of catch handlers which are already successors of this block.
    int formerCatchHandlersCount = catchSuccessors.size();

    // Then add catch handlers from the other block to the catch handler list.
    for (int i = 0; i < prevCatchTargets.size(); i++) {
      int prevCatchTarget = prevCatchTargets.get(i);
      DexType prevCatchGuard = prevCatchGuards.get(i);
      // TODO(sgjesse): Check sub-types of guards. Will require AppInfoWithSubtyping.
      if (newCatchGuards.contains(prevCatchGuard)) {
        continue;
      }
      BasicBlock catchSuccessor = fromBlock.successors.get(prevCatchTarget);
      // We assume that all the catch handlers targets has only one
      // predecessor and, thus, no phis.
      assert catchSuccessor.getPredecessors().size() == 1;
      assert catchSuccessor.getPhis().isEmpty();

      int index = catchSuccessors.indexOf(catchSuccessor);
      if (index == -1) {
        catchSuccessors.add(catchSuccessor);
        index = catchSuccessors.size() - 1;
      }
      newCatchGuards.add(prevCatchGuard);
      newCatchTargets.add(index);
    }

    // Create the new successors list and link things up.
    List<BasicBlock> formerSuccessors = new ArrayList<>(successors);
    successors.clear();
    List<BasicBlock> sharedCatchSuccessors = new ArrayList<>();
    for (int i = 0; i < catchSuccessors.size(); i++) {
      if (i < formerCatchHandlersCount) {
        // Former catch successors are just copied, as they are already linked.
        assert catchSuccessors.get(i).getPredecessors().contains(this);
        successors.add(catchSuccessors.get(i));
      } else {
        // New catch successors are linked properly.
        assert !catchSuccessors.get(i).getPredecessors().contains(this);
        link(catchSuccessors.get(i));
        sharedCatchSuccessors.add(catchSuccessors.get(i));
      }
    }
    catchHandlers = new CatchHandlers<>(newCatchGuards, newCatchTargets);

    // Finally add the normal successor if any.
    int catchSuccessorsCount = successors.size();
    for (BasicBlock formerSuccessor : formerSuccessors) {
      if (!successors.contains(formerSuccessor)) {
        assert !exit().isThrow();
        successors.add(formerSuccessor);
      }
    }
    assert successors.size() == catchSuccessorsCount || !exit().isThrow();

    return sharedCatchSuccessors;
  }

  /**
   * Return true if there is a path from the current {@link BasicBlock} to {@code target} or if
   * {@code target} is the same block than the current {@link BasicBlock}.
   */
  public boolean hasPathTo(BasicBlock target) {
    List<BasicBlock> visitedBlocks = new ArrayList<>();
    ArrayDeque<BasicBlock> blocks = new ArrayDeque<>();
    blocks.push(this);

    while (!blocks.isEmpty()) {
      BasicBlock block = blocks.pop();
      if (block == target) {
        return true;
      }
      visitedBlocks.add(block);
      for (BasicBlock blockToVisit : block.getSuccessors()) {
        if (!visitedBlocks.contains(blockToVisit)) {
          blocks.push(blockToVisit);
        }
      }
    }

    return false;
  }

  private static class PhiEquivalence extends Equivalence<Phi> {
    @Override
    protected boolean doEquivalent(Phi a, Phi b) {
      assert a.getBlock() == b.getBlock();
      for (int i = 0; i < a.getOperands().size(); i++) {
        if (a.getOperand(i) != b.getOperand(i)) {
          return false;
        }
      }
      return true;
    }

    @Override
    protected int doHash(Phi phi) {
      int hash = 0;
      for (Value value : phi.getOperands()) {
        hash = hash * 13 + value.hashCode();
      }
      return hash;
    }
  }

  public void deduplicatePhis() {
    PhiEquivalence equivalence = new PhiEquivalence();
    HashMap<Wrapper<Phi>, Phi> wrapper2phi = new HashMap<>();
    Iterator<Phi> phiIt = phis.iterator();
    while (phiIt.hasNext()) {
      Phi phi = phiIt.next();
      Wrapper<Phi> key = equivalence.wrap(phi);
      Phi replacement = wrapper2phi.get(key);
      if (replacement != null) {
        phi.replaceUsers(replacement);
        for (Value operand : phi.getOperands()) {
          operand.removePhiUser(phi);
        }
        phiIt.remove();
      } else {
        wrapper2phi.put(key, phi);
      }
    }
  }
}
