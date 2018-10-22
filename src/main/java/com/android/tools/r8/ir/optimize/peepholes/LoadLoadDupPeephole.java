// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.peepholes;

import com.android.tools.r8.ir.code.Dup;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Load;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.StackValues;
import com.google.common.collect.ImmutableList;

/**
 * {@link LoadLoadDupPeephole} looks for the following pattern:
 *
 * <pre>
 * Load                 s0 <- v0
 * Load                 s0 <- v0
 * </pre>
 *
 * and replaces with:
 *
 * <pre>
 * Load                 s0 <- v0
 * Dup
 * </pre>
 *
 * This saves a load and also removes a use of v0.
 */
public class LoadLoadDupPeephole implements BasicBlockPeephole {

  // This searches in reverse, so the pattern is build from the bottom.
  private final Point bottomLoadExp =
      new Point(PeepholeHelper.withoutLocalInfo(Instruction::isLoad));
  private final Point topLoadExp = new Point(Instruction::isLoad);

  private final PeepholeLayout layout = PeepholeLayout.lookBackward(bottomLoadExp, topLoadExp);

  @Override
  public boolean match(InstructionListIterator it) {
    Match match = layout.test(it);
    if (match == null) {
      return false;
    }
    Load bottomLoad = bottomLoadExp.get(match).asLoad();
    Load topLoad = topLoadExp.get(match).asLoad();
    if (topLoad.src() != bottomLoad.src() || topLoad.src().hasLocalInfo()) {
      return false;
    }

    StackValue src = (StackValue) topLoad.outValue();
    src.removeUser(bottomLoad);

    int height = src.getHeight();
    StackValue newSrc = src.duplicate(height);
    StackValue newTop = src.duplicate(height + 1);
    StackValues dest = new StackValues(ImmutableList.of(newSrc, newTop));

    bottomLoad.outValue().replaceUsers(newSrc);
    it.replaceCurrentInstruction(new Dup(dest, src));
    return true;
  }
}
