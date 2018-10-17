// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.peepholes;

import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import java.util.List;
import java.util.function.Predicate;

public class PeepholeHelper {

  public static class PeepholeLayout {
    private Instruction[] instructions;
    private List<Predicate<Instruction>> predicates;

    public PeepholeLayout(Instruction[] instructions, List<Predicate<Instruction>> predicates) {
      this.instructions = instructions;
      this.predicates = predicates;
    }

    public Instruction[] test(InstructionListIterator it) {
      int index = 0;
      boolean success = true;
      for (Predicate<Instruction> p : predicates) {
        if (!it.hasNext()) {
          success = false;
          break;
        }
        int insertIndex = index++;
        instructions[insertIndex] = it.next();
        if (!p.test(instructions[insertIndex])) {
          success = false;
          break;
        }
      }
      for (int i = 0; i < index; i++) {
        it.previous();
      }
      return success ? instructions : null;
    }
  }

  public static PeepholeLayout getLayout(List<Predicate<Instruction>> predicates) {
    Instruction[] arr = new Instruction[predicates.size()];
    return new PeepholeLayout(arr, predicates);
  }

  public static void swapNextTwoInstructions(InstructionListIterator it) {
    assert it.hasNext();
    Instruction moveForward = it.next();
    Instruction moveBack = it.next();
    it.set(moveForward);
    // Two calls to previous is needed because the iterator moves between elements.
    it.previous();
    it.previous();
    it.set(moveBack);
    it.next();
  }

  public static void resetNext(InstructionListIterator it, int count) {
    for (int i = 0; i < count; i++) {
      it.previous();
    }
  }
}
