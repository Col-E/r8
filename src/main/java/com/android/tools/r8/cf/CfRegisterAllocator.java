// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static com.android.tools.r8.dex.Constants.U16BIT_MAX;
import static com.android.tools.r8.ir.code.IRCode.INSTRUCTION_NUMBER_DELTA;
import static com.android.tools.r8.ir.regalloc.LiveIntervals.NO_REGISTER;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.regalloc.LiveIntervals;
import com.android.tools.r8.ir.regalloc.LiveIntervalsUse;
import com.android.tools.r8.ir.regalloc.LiveRange;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.utils.InternalOptions;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * Allocator for assigning local-indexes to non-stack values.
 *
 * <p>This is mostly a very simple variant of {@link
 * com.android.tools.r8.ir.regalloc.LinearScanRegisterAllocator} since for CF there are no register
 * constraints and thus no need for spilling.
 */
public class CfRegisterAllocator implements RegisterAllocator {

  private final IRCode code;
  private final InternalOptions options;

  // Mapping from basic blocks to the set of values live at entry to that basic block.
  private Map<BasicBlock, Set<Value>> liveAtEntrySets;

  // List of all top-level live intervals for all SSA values.
  private final List<LiveIntervals> liveIntervals = new ArrayList<>();

  // List of active intervals.
  private final List<LiveIntervals> active = new LinkedList<>();

  // List of intervals where the current instruction falls into one of their live range holes.
  private final List<LiveIntervals> inactive = new LinkedList<>();

  // List of intervals that no register has been allocated to sorted by first live range.
  private final PriorityQueue<LiveIntervals> unhandled = new PriorityQueue<>();

  // The set of registers that are free for allocation.
  private final NavigableSet<Integer> freeRegisters = new TreeSet<>();

  private int nextUnusedRegisterNumber = 0;

  private int maxRegisterNumber = 0;

  public CfRegisterAllocator(IRCode code, InternalOptions options) {
    this.code = code;
    this.options = options;
  }

  @Override
  public int registersUsed() {
    return maxRegisterNumber + 1;
  }

  @Override
  public int getRegisterForValue(Value value, int instructionNumber) {
    return getRegisterForValue(value);
  }

  public int getRegisterForValue(Value value) {
    if (value instanceof FixedLocalValue) {
      return ((FixedLocalValue) value).getRegister(this);
    }
    assert !value.getLiveIntervals().hasSplits();
    return value.getLiveIntervals().getRegister();
  }

  @Override
  public boolean argumentValueUsesHighRegister(Value value, int instructionNumber) {
    throw new Unreachable();
  }

  @Override
  public int getArgumentOrAllocateRegisterForValue(Value value, int instructionNumber) {
    throw new Unreachable();
  }

  @Override
  public void allocateRegisters(boolean debug) {
    assert options.debug == debug;
    allocateRegisters();
  }

  public void allocateRegisters() {
    computeNeedsRegister();
    computeLivenessInformation();
    performLinearScan();
  }

  private void computeNeedsRegister() {
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      Instruction next = it.next();
      Value outValue = next.outValue();
      if (outValue != null) {
        outValue.setNeedsRegister(!(outValue instanceof StackValue));
      }
    }
  }

  private void computeLivenessInformation() {
    code.numberInstructions();
    liveAtEntrySets = code.computeLiveAtEntrySets();
    computeLiveRanges();
  }

  private void computeLiveRanges() {
    for (BasicBlock block : code.topologicallySortedBlocks()) {
      Set<Value> live = new HashSet<>();
      List<BasicBlock> successors = block.getSuccessors();
      Set<Value> phiOperands = new HashSet<>();
      for (BasicBlock successor : successors) {
        live.addAll(liveAtEntrySets.get(successor));
        int predIndex = successor.getPredecessors().indexOf(block);
        for (Phi phi : successor.getPhis()) {
          live.remove(phi);
          phiOperands.add(phi.getOperand(predIndex));
          assert phi.getDebugValues().stream().allMatch(Value::needsRegister);
          phiOperands.addAll(phi.getDebugValues());
        }
      }
      live.addAll(phiOperands);
      List<Instruction> instructions = block.getInstructions();
      for (Value value : live) {
        int end = block.entry().getNumber() + instructions.size() * INSTRUCTION_NUMBER_DELTA;
        // Make sure that phi operands do not overlap the phi live range. The phi operand is
        // not live until the next instruction, but only until the gap before the next instruction
        // where the phi value takes over.
        if (phiOperands.contains(value)) {
          end--;
        }
        addLiveRange(value, block, end);
      }
      InstructionListIterator it = block.listIterator(block.getInstructions().size());
      while (it.hasPrevious()) {
        Instruction instruction = it.previous();
        int number = instruction.getNumber();
        Value definition = instruction.outValue();
        if (definition != null) {
          // For instructions that define values which have no use create a live range covering
          // the instruction. This will typically be instructions that can have side effects even
          // if their output is not used.
          if (!definition.isUsed()) {
            addLiveRange(definition, block, number + INSTRUCTION_NUMBER_DELTA);
            assert instruction.isArgument()
                : "Arguments should be the only potentially unused local in CF";
          }
          live.remove(definition);
        }
        for (Value use : instruction.inValues()) {
          if (use.needsRegister()) {
            if (!live.contains(use)) {
              live.add(use);
              addLiveRange(use, block, number);
            }
            LiveIntervals useIntervals = use.getLiveIntervals();
            useIntervals.addUse(new LiveIntervalsUse(number, U16BIT_MAX /* unconstrained */));
          }
        }
        if (options.debug) {
          for (Value use : instruction.getDebugValues()) {
            assert use.needsRegister();
            if (!live.contains(use)) {
              live.add(use);
              addLiveRange(use, block, number);
            }
          }
        }
      }
    }
  }

  private void addLiveRange(Value value, BasicBlock block, int end) {
    int firstInstructionInBlock = block.entry().getNumber();
    int instructionsSize = block.getInstructions().size() * INSTRUCTION_NUMBER_DELTA;
    int lastInstructionInBlock =
        firstInstructionInBlock + instructionsSize - INSTRUCTION_NUMBER_DELTA;
    int instructionNumber;
    if (value.isPhi()) {
      instructionNumber = firstInstructionInBlock;
    } else {
      Instruction instruction = value.definition;
      instructionNumber = instruction.getNumber();
    }
    if (value.getLiveIntervals() == null) {
      Value current = value.getStartOfConsecutive();
      LiveIntervals intervals = new LiveIntervals(current);
      while (true) {
        liveIntervals.add(intervals);
        Value next = current.getNextConsecutive();
        if (next == null) {
          break;
        }
        LiveIntervals nextIntervals = new LiveIntervals(next);
        intervals.link(nextIntervals);
        current = next;
        intervals = nextIntervals;
      }
    }
    LiveIntervals intervals = value.getLiveIntervals();
    if (firstInstructionInBlock <= instructionNumber
        && instructionNumber <= lastInstructionInBlock) {
      if (value.isPhi()) {
        // Phis need to interfere with spill restore moves inserted before the instruction because
        // the phi value is defined on the inflowing edge.
        instructionNumber--;
      }
      intervals.addRange(new LiveRange(instructionNumber, end));
      if (!value.isPhi()) {
        intervals.addUse(new LiveIntervalsUse(instructionNumber, U16BIT_MAX /* unconstrained */));
      }
    } else {
      intervals.addRange(new LiveRange(firstInstructionInBlock - 1, end));
    }
  }

  private void performLinearScan() {
    unhandled.addAll(liveIntervals);
    while (!unhandled.isEmpty()) {
      LiveIntervals unhandledInterval = unhandled.poll();
      int start = unhandledInterval.getStart();
      {
        // Check for active intervals that expired or became inactive.
        Iterator<LiveIntervals> it = active.iterator();
        while (it.hasNext()) {
          LiveIntervals activeIntervals = it.next();
          if (start >= activeIntervals.getEnd()) {
            it.remove();
            freeRegistersForIntervals(activeIntervals);
          } else if (!activeIntervals.overlapsPosition(start)) {
            assert activeIntervals.getRegister() != NO_REGISTER;
            it.remove();
            inactive.add(activeIntervals);
            freeRegistersForIntervals(activeIntervals);
          }
        }
      }
      IntList reactivedBeforeEnd = new IntArrayList(inactive.size());
      {
        // Check for inactive intervals that expired or become active.
        Iterator<LiveIntervals> it = inactive.iterator();
        while (it.hasNext()) {
          LiveIntervals inactiveIntervals = it.next();
          if (start >= inactiveIntervals.getEnd()) {
            it.remove();
          } else if (inactiveIntervals.overlapsPosition(start)) {
            it.remove();
            assert inactiveIntervals.getRegister() != NO_REGISTER;
            active.add(inactiveIntervals);
            takeRegistersForIntervals(inactiveIntervals);
          } else if (inactiveIntervals.overlaps(unhandledInterval)) {
            reactivedBeforeEnd.add(inactiveIntervals.getRegister());
            takeRegistersForIntervals(inactiveIntervals);
          }
        }
      }

      // Perform the actual allocation.
      assignRegisterToUnhandledInterval(
          unhandledInterval, getNextFreeRegister(unhandledInterval.getType().isWide()));

      // Add back the potentially free registers from the inactive set.
      freeRegisters.addAll(reactivedBeforeEnd);
    }
  }

  // Note that getNextFreeRegister will not take the register before it is committed to an interval.
  private int getNextFreeRegister(boolean isWide) {
    if (!freeRegisters.isEmpty()) {
      if (isWide) {
        for (Integer register : freeRegisters) {
          if (freeRegisters.contains(register + 1) || nextUnusedRegisterNumber == register + 1) {
            return register;
          }
        }
        return nextUnusedRegisterNumber;
      }
      return freeRegisters.first();
    }
    return nextUnusedRegisterNumber;
  }

  private void freeRegistersForIntervals(LiveIntervals intervals) {
    int register = intervals.getRegister();
    freeRegisters.add(register);
    if (intervals.getType().isWide()) {
      freeRegisters.add(register + 1);
    }
  }

  private void takeRegistersForIntervals(LiveIntervals intervals) {
    int register = intervals.getRegister();
    freeRegisters.remove(register);
    if (intervals.getType().isWide()) {
      freeRegisters.remove(register + 1);
    }
  }

  private void assignRegisterToUnhandledInterval(LiveIntervals unhandledInterval, int register) {
    assignRegister(unhandledInterval, register);
    takeRegistersForIntervals(unhandledInterval);
    updateRegisterState(register, unhandledInterval.getType().isWide());
    active.add(unhandledInterval);
  }

  private void updateRegisterState(int register, boolean needsRegisterPair) {
    int maxRegister = register + (needsRegisterPair ? 1 : 0);
    if (maxRegister >= nextUnusedRegisterNumber) {
      nextUnusedRegisterNumber = maxRegister + 1;
    }
    maxRegisterNumber = Math.max(maxRegisterNumber, maxRegister);
  }

  private void assignRegister(LiveIntervals intervals, int register) {
    intervals.setRegister(register);
  }

  public Collection<Value> getLocalsAtPosition(int blockEntryInstruction) {
    List<Value> values = new ArrayList<>(registersUsed());
    for (LiveIntervals intervals : liveIntervals) {
      addLiveValueFromIntervals(blockEntryInstruction, intervals, values);
    }
    return values;
  }

  private static void addLiveValueFromIntervals(
      int position, LiveIntervals intervals, List<Value> values) {
    if (intervals.overlapsPosition(position)) {
      for (LiveRange range : intervals.getRanges()) {
        if (range.start <= position && position < range.end) {
          values.add(intervals.getValue());
          return;
        }
      }
    }
  }
}
