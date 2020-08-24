// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.peepholes;

import com.android.tools.r8.ir.code.DebugPosition;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Position;

/**
 * {@link RemoveDebugPositionPeephole} looks for the following two patterns:
 *
 * <pre>
 *   p: DebugPosition
 *   [q: Const]*
 *   q: Instr // TODO(b/166074600): This must currently be a Const.
 * </pre>
 *
 * if p = q:
 *
 * <pre>
 *   [q: Const]*
 *   q: Instr
 * </pre>
 *
 * if p != q and size([q: Const]*) > 0:
 *
 * <pre>
 *   [p: Const]*
 *   q: Instr
 * </pre>
 *
 * This rewrite will eliminate debug positions that can be placed on a constant, retaining the line
 * but avoiding the nop resulting in a remaining position instruction.
 */
public class RemoveDebugPositionPeephole implements BasicBlockPeephole {

  private final Point debugPositionExp = new Point(Instruction::isDebugPosition);
  private final Point secondInstructionExp =
      new Point(
          // TODO(b/166074600): It should be possible to match on any materializing instruction
          //  here. The phi-optimization seems to invalidate that by changing stack operations.
          Instruction::isConstInstruction);

  private final PeepholeLayout layout =
      PeepholeLayout.lookForward(debugPositionExp, secondInstructionExp);

  @Override
  public boolean match(InstructionListIterator it) {
    Match match = layout.test(it);
    if (match == null) {
      return false;
    }
    DebugPosition debugPosition = debugPositionExp.get(match).asDebugPosition();
    Instruction secondInstruction = secondInstructionExp.get(match);

    // If the position is the same on the following instruction it can simply be removed.
    Position position = debugPosition.getPosition();
    if (position.equals(secondInstruction.getPosition())) {
      it.removeOrReplaceByDebugLocalRead();
      return true;
    }

    boolean movedPosition = false;
    it.next(); // skip debug position.
    Instruction current = it.next(); // start loop at second instruction.
    assert current == secondInstruction;
    while (current.isConstInstruction() && it.hasNext()) {
      Instruction next = it.next();
      if (!next.getPosition().equals(current.getPosition())) {
        break;
      }
      // The constant shares position with the next instruction so it subsumes the position of
      // the debug position.
      movedPosition = true;
      current.forceOverwritePosition(position);
      current = next;
    }
    it.previousUntil(i -> i == debugPosition);
    if (movedPosition) {
      it.next();
      it.removeOrReplaceByDebugLocalRead();
      return true;
    }
    return false;
  }

  @Override
  public boolean resetAfterMatch() {
    return false;
  }
}
