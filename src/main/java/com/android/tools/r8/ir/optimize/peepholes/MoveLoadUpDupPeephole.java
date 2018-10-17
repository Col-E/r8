// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.peepholes;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.code.Dup;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Load;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.StackValues;
import com.android.tools.r8.ir.optimize.peepholes.PeepholeHelper.PeepholeLayout;
import com.google.common.collect.ImmutableList;

/**
 * {@link MoveLoadUpDupPeephole} looks for the following pattern:
 *
 * <pre>
 * Load                 s0 <- v0
 * Invoke               s0; method: void
 * Load                 s0 <- v0
 * </pre>
 *
 * and replaces with:
 *
 * <pre>
 * Load                 s0 <- v0
 * Dup                  [s0, s1] <- s0
 * Invoke               s1; method: void
 * </pre>
 *
 * This saves a load and removes a use of v0.
 */
public class MoveLoadUpDupPeephole implements BasicBlockPeephole {

  private final PeepholeLayout layout =
      PeepholeHelper.getLayout(
          ImmutableList.of(Instruction::isLoad, Instruction::isInvoke, Instruction::isLoad));

  @Override
  public boolean match(InstructionListIterator it, DexItemFactory factory) {
    Instruction[] match = layout.test(it);
    if (match == null) {
      return false;
    }
    Load load1 = match[0].asLoad();
    Invoke invoke = match[1].asInvoke();
    Load load2 = match[2].asLoad();
    if (load1.src().hasLocalInfo()) {
      return false;
    }
    boolean invokeArgsIsLoad =
        invoke.inValues().size() == 1 && invoke.inValues().get(0) == load1.outValue();
    boolean isLoadSameSource = load1.src() == load2.src();
    boolean isVoid = invoke.hasReturnTypeVoid(factory);
    if (!invokeArgsIsLoad || !isLoadSameSource || !isVoid) {
      return false;
    }
    // Pattern matched, keep the first load.
    it.next();
    // Swap the two instructions so that we can overwrite.
    PeepholeHelper.swapNextTwoInstructions(it);
    // Prepare the dup instruction by virtually removing the top of the stack and replace it with
    // two stack values.
    StackValue src = (StackValue) load1.outValue();
    int height = src.getHeight();
    StackValue newSrc = src.duplicate(height);
    StackValue newTop = src.duplicate(height + 1);
    StackValues dest = new StackValues(ImmutableList.of(newSrc, newTop));
    // Update the use of out of load2 to new source
    load2.outValue().replaceUsers(newSrc);
    it.replaceCurrentInstruction(new Dup(dest, src));
    it.next();
    // Finally, update the in-value for invoke to be the new top element.
    invoke.replaceValue(0, newTop);
    // Finished modifying this part of the code.
    PeepholeHelper.resetNext(it, 3);
    return true;
  }
}
