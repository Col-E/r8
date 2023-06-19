// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.StringSwitch;
import com.android.tools.r8.ir.code.Switch;
import com.android.tools.r8.utils.BooleanUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Comparator;
import java.util.function.IntPredicate;

/** Helper to remove dead switch cases from a switch instruction. */
class SwitchCaseEliminator {

  private final BasicBlock block;
  private final BasicBlock defaultTarget;
  private final InstructionListIterator iterator;
  private final Switch theSwitch;

  private int alwaysHitCase = -1;
  private BasicBlock alwaysHitTarget;
  private boolean liveFallthrough = true;
  private boolean mayHaveIntroducedUnreachableBlocks = false;
  private IntSet switchCasesToBeRemoved;

  SwitchCaseEliminator(Switch theSwitch, InstructionListIterator iterator) {
    this.block = theSwitch.getBlock();
    this.defaultTarget = theSwitch.fallthroughBlock();
    this.iterator = iterator;
    this.theSwitch = theSwitch;
  }

  private boolean allSwitchCasesMarkedForRemoval() {
    return switchCasesToBeRemoved != null
        && switchCasesToBeRemoved.size() == theSwitch.numberOfKeys();
  }

  boolean canBeOptimized() {
    assert switchCasesToBeRemoved == null || !switchCasesToBeRemoved.isEmpty();
    return switchCasesToBeRemoved != null || hasAlwaysHitCase() || !isFallthroughLive();
  }

  boolean mayHaveIntroducedUnreachableBlocks() {
    return mayHaveIntroducedUnreachableBlocks;
  }

  public boolean isSwitchCaseLive(int index) {
    if (hasAlwaysHitCase()) {
      return index == alwaysHitCase;
    }
    if (switchCasesToBeRemoved != null) {
      return !switchCasesToBeRemoved.contains(index);
    }
    assert !isFallthroughLive();
    return true;
  }

  public boolean isFallthroughDead() {
    return !isFallthroughLive();
  }

  public boolean isFallthroughLive() {
    return liveFallthrough;
  }

  public boolean hasAlwaysHitCase() {
    return alwaysHitCase >= 0;
  }

  void markSwitchCaseAsAlwaysHit(int i) {
    assert alwaysHitCase < 0;
    alwaysHitCase = i;
    alwaysHitTarget = theSwitch.targetBlock(i);
    liveFallthrough = false;
  }

  void markSwitchCaseForRemoval(int i) {
    if (switchCasesToBeRemoved == null) {
      switchCasesToBeRemoved = new IntOpenHashSet();
    }
    switchCasesToBeRemoved.add(i);
  }

  void markSwitchFallthroughAsNeverHit() {
    assert !hasAlwaysHitCase();
    liveFallthrough = false;
  }

  void optimize() {
    if (canBeOptimized()) {
      int originalNumberOfSuccessors = block.getSuccessors().size();
      IntList removedSuccessorIndices = unlinkDeadSuccessors();
      if (hasAlwaysHitCase() || allSwitchCasesMarkedForRemoval()) {
        // Replace switch with a simple goto.
        replaceSwitchByGoto();
      } else {
        // Replace switch by a new switch where the dead switch cases have been removed.
        replaceSwitchByOptimizedSwitch(originalNumberOfSuccessors, removedSuccessorIndices);
      }
    }
  }

  private IntList unlinkDeadSuccessors() {
    IntPredicate successorHasBecomeDeadPredicate = computeSuccessorHasBecomeDeadPredicate();
    IntList successorIndicesToBeRemoved = new IntArrayList();
    for (int i = 0; i < block.getSuccessors().size(); i++) {
      if (successorHasBecomeDeadPredicate.test(i)) {
        BasicBlock successor = block.getSuccessors().get(i);
        successor.removePredecessor(block, null);
        successorIndicesToBeRemoved.add(i);
        if (successor.getPredecessors().isEmpty()) {
          mayHaveIntroducedUnreachableBlocks = true;
        }
      }
    }
    successorIndicesToBeRemoved.sort(Comparator.naturalOrder());
    block.removeSuccessorsByIndex(successorIndicesToBeRemoved);
    return successorIndicesToBeRemoved;
  }

  private IntPredicate computeSuccessorHasBecomeDeadPredicate() {
    int[] numberOfControlFlowEdgesToBlockWithIndex = new int[block.getSuccessors().size()];
    for (int i = 0; i < theSwitch.numberOfKeys(); i++) {
      if (isSwitchCaseLive(i)) {
        int targetBlockIndex = theSwitch.getTargetBlockIndex(i);
        numberOfControlFlowEdgesToBlockWithIndex[targetBlockIndex] += 1;
      }
    }
    if (isFallthroughLive()) {
      numberOfControlFlowEdgesToBlockWithIndex[theSwitch.getFallthroughBlockIndex()] += 1;
    }
    for (int i : block.getCatchHandlersWithSuccessorIndexes().getUniqueTargets()) {
      numberOfControlFlowEdgesToBlockWithIndex[i] += 1;
    }
    return i -> numberOfControlFlowEdgesToBlockWithIndex[i] == 0;
  }

  private void replaceSwitchByGoto() {
    assert !hasAlwaysHitCase() || alwaysHitTarget != null;
    BasicBlock target = hasAlwaysHitCase() ? alwaysHitTarget : defaultTarget;
    iterator.replaceCurrentInstruction(new Goto(target));
  }

  private void replaceSwitchByOptimizedSwitch(
      int originalNumberOfSuccessors, IntList removedSuccessorIndices) {
    int[] targetBlockIndexOffset = new int[originalNumberOfSuccessors];
    for (int removedSuccessorIndex : removedSuccessorIndices) {
      targetBlockIndexOffset[removedSuccessorIndex] = 1;
    }
    for (int i = 1; i < targetBlockIndexOffset.length; i++) {
      targetBlockIndexOffset[i] += targetBlockIndexOffset[i - 1];
    }

    int newFallthrough = theSwitch.numberOfKeys();
    if (isFallthroughDead()) {
      for (int i = theSwitch.numberOfKeys() - 1; i >= 0; i--) {
        if (isSwitchCaseLive(i)) {
          newFallthrough = i;
          break;
        }
      }
    }

    // Compute the number of removed switch cases. If the fallthrough is dead, we promote one of the
    // live switch cases to being the fallthrough instead.
    int numberOfRemovedSwitchCases =
        switchCasesToBeRemoved != null ? switchCasesToBeRemoved.size() : 0;
    numberOfRemovedSwitchCases += BooleanUtils.intValue(isFallthroughDead());

    int newNumberOfKeys = theSwitch.numberOfKeys() - numberOfRemovedSwitchCases;
    int[] newTargetBlockIndices = new int[newNumberOfKeys];
    for (int i = 0, j = 0; i < newFallthrough; i++) {
      if (isSwitchCaseLive(i)) {
        newTargetBlockIndices[j] =
            theSwitch.getTargetBlockIndex(i)
                - targetBlockIndexOffset[theSwitch.getTargetBlockIndex(i)];
        assert newTargetBlockIndices[j] < block.getSuccessors().size();
        j++;
      }
    }

    int fallthroughBlockIndex;
    if (isFallthroughLive()) {
      fallthroughBlockIndex =
          theSwitch.getFallthroughBlockIndex()
              - targetBlockIndexOffset[theSwitch.getFallthroughBlockIndex()];
    } else {
      fallthroughBlockIndex =
          theSwitch.getTargetBlockIndex(newFallthrough)
              - targetBlockIndexOffset[theSwitch.getTargetBlockIndex(newFallthrough)];
    }

    Switch replacement;
    if (theSwitch.isIntSwitch()) {
      IntSwitch intSwitch = theSwitch.asIntSwitch();
      int[] newKeys = new int[newNumberOfKeys];
      for (int i = 0, j = 0; i < newFallthrough; i++) {
        if (isSwitchCaseLive(i)) {
          newKeys[j] = intSwitch.getKey(i);
          j++;
        }
      }
      replacement =
          new IntSwitch(theSwitch.value(), newKeys, newTargetBlockIndices, fallthroughBlockIndex);
    } else {
      assert theSwitch.isStringSwitch();
      StringSwitch stringSwitch = theSwitch.asStringSwitch();
      DexString[] newKeys = new DexString[newNumberOfKeys];
      for (int i = 0, j = 0; i < newFallthrough; i++) {
        if (isSwitchCaseLive(i)) {
          newKeys[j] = stringSwitch.getKey(i);
          j++;
        }
      }
      replacement =
          new StringSwitch(
              theSwitch.value(), newKeys, newTargetBlockIndices, fallthroughBlockIndex);
    }
    iterator.replaceCurrentInstruction(replacement);
  }
}
