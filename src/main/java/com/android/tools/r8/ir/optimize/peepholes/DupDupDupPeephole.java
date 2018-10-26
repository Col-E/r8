// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.peepholes;

import com.android.tools.r8.ir.code.Dup;
import com.android.tools.r8.ir.code.Dup2;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.StackValues;
import com.google.common.collect.ImmutableList;

/**
 * Peephole that looks for the following pattern:
 *
 * <pre>
 * Dup
 * Dup
 * Dup
 * </pre>
 *
 * and replaces with
 *
 * <pre>
 * Dup
 * Dup2
 * </pre>
 */
public class DupDupDupPeephole implements BasicBlockPeephole {

  private final Point dup1Exp =
      new Point((i) -> i.isDup() && !i.inValues().get(0).getTypeLattice().isWide());
  private final Point dup2Exp =
      new Point((i) -> i.isDup() && !i.inValues().get(0).getTypeLattice().isWide());
  private final Point dup3Exp =
      new Point((i) -> i.isDup() && !i.inValues().get(0).getTypeLattice().isWide());

  private final PeepholeLayout layout = PeepholeLayout.lookBackward(dup1Exp, dup2Exp, dup3Exp);

  @Override
  public boolean match(InstructionListIterator it) {
    Match match = layout.test(it);
    if (match == null) {
      return false;
    }

    Dup dupTop = dup3Exp.get(match).asDup();
    Dup dupMiddle = dup2Exp.get(match).asDup();
    Dup dupBottom = dup1Exp.get(match).asDup();

    StackValue src = (StackValue) dupTop.inValues().get(0);
    StackValue srcMiddle = (StackValue) dupMiddle.inValues().get(0);
    StackValue srcBottom = (StackValue) dupBottom.inValues().get(0);

    StackValues tv = (StackValues) dupTop.outValue();
    StackValues mv = (StackValues) dupMiddle.outValue();
    StackValues bv = (StackValues) dupBottom.outValue();

    // The stack looks like:
    // ..., tv0, mv0, bv0, bv1,.. -->
    // because tv1 was used by dupMiddle and mv1 was used by dupBottom.

    StackValue tv0Dup2 = tv.getStackValues().get(0).duplicate(src.getHeight());
    StackValue mv0Dup2 = mv.getStackValues().get(0).duplicate(src.getHeight() + 1);
    StackValue bv0Dup2 = bv.getStackValues().get(0).duplicate(src.getHeight() + 2);
    StackValue bv1Dup2 = bv.getStackValues().get(1).duplicate(src.getHeight() + 3);

    // Remove tv1 use.
    srcMiddle.removeUser(dupMiddle);
    // Remove mv1 use.
    srcBottom.removeUser(dupBottom);
    // Replace other uses.
    tv.getStackValues().get(0).replaceUsers(tv0Dup2);
    mv.getStackValues().get(0).replaceUsers(mv0Dup2);
    bv.getStackValues().get(0).replaceUsers(bv0Dup2);
    bv.getStackValues().get(1).replaceUsers(bv1Dup2);

    StackValues dest = new StackValues(ImmutableList.of(tv0Dup2, mv0Dup2, bv0Dup2, bv1Dup2));

    Dup2 dup2 = new Dup2(dest, tv.getStackValues().get(0), tv.getStackValues().get(1));

    it.removeOrReplaceByDebugLocalRead();
    it.previous();
    it.replaceCurrentInstruction(dup2);

    // Reset the pointer
    PeepholeHelper.resetPrevious(it, 1);
    return true;
  }
}
