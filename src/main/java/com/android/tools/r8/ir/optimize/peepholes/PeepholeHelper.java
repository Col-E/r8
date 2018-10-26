// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.peepholes;

import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.StackValues;
import com.android.tools.r8.ir.code.Value;
import java.util.function.Predicate;

public class PeepholeHelper {

  public static Predicate<Instruction> withoutLocalInfo(Predicate<Instruction> predicate) {
    return t ->
        predicate.test(t)
            && !t.hasInValueWithLocalInfo()
            && (t.outValue() == null || !t.outValue().hasLocalInfo());
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

  public static void resetPrevious(InstructionListIterator it, int count) {
    for (int i = 0; i < count; i++) {
      it.next();
    }
  }

  public static int numberOfValuesPutOnStack(Instruction instruction) {
    Value outValue = instruction.outValue();
    if (outValue instanceof StackValue) {
      return 1;
    }
    if (outValue instanceof StackValues) {
      return ((StackValues) outValue).getStackValues().size();
    }
    return 0;
  }

  public static int numberOfValuesConsumedFromStack(Instruction instruction) {
    int count = 0;
    for (int i = instruction.inValues().size() - 1; i >= 0; i--) {
      if (instruction.inValues().get(i) instanceof StackValue) {
        count += 1;
      }
    }
    return count;
  }
}
