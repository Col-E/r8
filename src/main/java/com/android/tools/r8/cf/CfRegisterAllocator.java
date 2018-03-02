// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static com.android.tools.r8.ir.regalloc.LiveIntervals.NO_REGISTER;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.regalloc.LinearScanRegisterAllocator;
import com.android.tools.r8.ir.regalloc.LiveIntervals;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
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
  private NavigableSet<Integer> freeRegisters = new TreeSet<>();

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
    return getRegisterForValue(value);
  }

  @Override
  public void allocateRegisters(boolean debug) {
    assert options.debug == debug;
    allocateRegisters();
  }

  public void allocateRegisters() {
    computeNeedsRegister();
    ImmutableList<BasicBlock> blocks = computeLivenessInformation();
    performLinearScan();
    if (options.debug) {
      LinearScanRegisterAllocator.computeDebugInfo(blocks, liveIntervals, this);
    }
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

  private ImmutableList<BasicBlock> computeLivenessInformation() {
    ImmutableList<BasicBlock> blocks = code.numberInstructions();
    liveAtEntrySets = code.computeLiveAtEntrySets();
    LinearScanRegisterAllocator.computeLiveRanges(options, code, liveAtEntrySets, liveIntervals);
    return blocks;
  }

  private void performLinearScan() {
    unhandled.addAll(liveIntervals);

    while (!unhandled.isEmpty() && unhandled.peek().getValue().isArgument()) {
      LiveIntervals argument = unhandled.poll();
      assignRegisterToUnhandledInterval(argument, getNextFreeRegister(argument.getType().isWide()));
    }

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
          }
        }
      }

      // Find a free register that is not used by an inactive interval that overlaps with
      // unhandledInterval.
      boolean wide = unhandledInterval.getType().isWide();
      int register;
      NavigableSet<Integer> previousFreeRegisters = new TreeSet<Integer>(freeRegisters);
      while (true) {
        register = getNextFreeRegister(wide);
        boolean overlapsInactiveInterval = false;
        for (LiveIntervals inactiveIntervals : inactive) {
          if (unhandledInterval.hasConflictingRegisters(inactiveIntervals)
              && unhandledInterval.overlaps(inactiveIntervals)) {
            overlapsInactiveInterval = true;
            break;
          }
        }
        if (!overlapsInactiveInterval) {
          break;
        }
        // Remove so that next invocation of getNextFreeRegister does not consider this.
        freeRegisters.remove(register);
        // For wide types, register + 1 and 2 might be good even though register + 0 and 1 weren't,
        // so don't remove register+1 from freeRegisters.
      }
      freeRegisters = previousFreeRegisters;
      assignRegisterToUnhandledInterval(unhandledInterval, register);
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

  public void addToLiveAtEntrySet(BasicBlock block, Collection<Phi> phis) {
    liveAtEntrySets.get(block).addAll(phis);
  }

  public Collection<Value> getLocalsAtBlockEntry(BasicBlock block) {
    return liveAtEntrySets.get(block);
  }
}
