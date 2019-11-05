// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.utils.DequeUtils;
import com.google.common.collect.Sets;
import java.util.Deque;
import java.util.Set;

public class IRCodeUtils {

  /**
   * Removes the given instruction and all the instructions that are used to define the in-values of
   * the given instruction, even if the instructions may have side effects (!).
   *
   * <p>Use with caution!
   */
  public static void removeInstructionAndTransitiveInputsIfNotUsed(
      IRCode code, Instruction instruction) {
    Set<InstructionOrPhi> removed = Sets.newIdentityHashSet();
    Deque<InstructionOrPhi> worklist = DequeUtils.newArrayDeque(instruction);
    while (!worklist.isEmpty()) {
      InstructionOrPhi instructionOrPhi = worklist.removeFirst();
      if (removed.contains(instructionOrPhi)) {
        // Already removed.
        continue;
      }
      if (instructionOrPhi.isPhi()) {
        Phi current = instructionOrPhi.asPhi();
        if (!current.hasUsers() && !current.hasDebugUsers()) {
          boolean hasOtherPhiUserThanSelf = false;
          for (Phi phiUser : current.uniquePhiUsers()) {
            if (phiUser != current) {
              hasOtherPhiUserThanSelf = true;
              break;
            }
          }
          if (!hasOtherPhiUserThanSelf) {
            current.removeDeadPhi();
            for (Value operand : current.getOperands()) {
              worklist.add(operand.isPhi() ? operand.asPhi() : operand.definition);
            }
            removed.add(current);
          }
        }
      } else {
        Instruction current = instructionOrPhi.asInstruction();
        if (!current.hasOutValue() || !current.outValue().hasAnyUsers()) {
          current.getBlock().listIterator(code, current).removeOrReplaceByDebugLocalRead();
          for (Value inValue : current.inValues()) {
            worklist.add(inValue.isPhi() ? inValue.asPhi() : inValue.definition);
          }
          removed.add(current);
        }
      }
    }
  }
}
