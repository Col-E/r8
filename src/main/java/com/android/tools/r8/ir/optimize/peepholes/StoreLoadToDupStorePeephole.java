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
import com.android.tools.r8.ir.code.Store;
import com.android.tools.r8.ir.code.Value;
import java.util.List;

/**
 * Peephole that looks for the following pattern:
 *
 * <pre>
 * Store                v0 <- sa
 * Load                 sb <- v0   # where v0 has multiple users
 * zero or more Dup/Dup2
 * </pre>
 *
 * and replace it with
 *
 * <pre>
 * zero or more Dup/Dup2
 * Dup            [sb, sc] <- sa
 * Store                v0 <- sc
 * </pre>
 */
public class StoreLoadToDupStorePeephole implements BasicBlockPeephole {

  private final Point storeExp = new Point(PeepholeHelper.withoutLocalInfo(Instruction::isStore));
  private final Point loadExp = new Point(Instruction::isLoad);
  private final Wildcard dupsExp = new Wildcard(i -> i.isDup() || i.isDup2());

  private final PeepholeLayout layout = PeepholeLayout.lookForward(storeExp, loadExp, dupsExp);

  @Override
  public boolean match(InstructionListIterator it) {
    Match match = layout.test(it);
    if (match == null) {
      return false;
    }
    Store oldStore = storeExp.get(match).asStore();
    Load load = loadExp.get(match).asLoad();
    if (load.src() != oldStore.outValue() || oldStore.outValue().numberOfAllUsers() <= 1) {
      return false;
    }
    List<Instruction> dups = dupsExp.get(match);
    Instruction lastDup = dups.isEmpty() ? null : dups.get(dups.size() - 1);
    StackValue oldStoreSrc = (StackValue) oldStore.src();
    Value loadOut = load.swapOutValue(null);
    if (lastDup == null) {
      // Replace
      //     Store                     v0 <- oldStoreSrc
      //     Load                 loadOut <- v0
      // with
      //     Dup   [loadOut, newStoreSrc] <- oldStoreSrc
      //     Store                     v0 <- newStoreSrc

      StackValue newStoreSrc = oldStoreSrc.duplicate(oldStoreSrc.getHeight() + 1);
      Dup dup = new Dup((StackValue) loadOut, newStoreSrc, oldStoreSrc);
      dup.setPosition(oldStore.getPosition());
      oldStore.replaceValue(0, newStoreSrc);
      it.add(dup);
      PeepholeHelper.resetPrevious(it, 2);
      it.removeOrReplaceByDebugLocalRead();
    } else {
      // Replace
      //     Store                         storeOut <- oldStoreSrc
      //     Load                           loadOut <- storeOut
      //     Dup                           [sa, sb] <- loadOut
      //     (Dup/Dup2)*
      //     Dup/Dup2           [..., topAfterDups] <- [...]           # lastDup
      // with
      //     Dup                           [sa, sb] <- oldStoreSrc
      //     (Dup/Dup2)*
      //     Dup/Dup2        [..., newTopAfterDups] <- [...]           # lastDup
      //     Dup        [topAfterDups, newStoreSrc] <- newTopAfterDups
      //     Store                         storeOut <- newStoreSrc
      Value storeOut = oldStore.swapOutValue(null);
      it.removeOrReplaceByDebugLocalRead(); // Remove Store.
      it.next();
      it.removeOrReplaceByDebugLocalRead(); // Remove Load.
      assert dups.get(0).isDup() && dups.get(0).inValues().get(0) == loadOut;
      dups.get(0).replaceValue(0, oldStoreSrc);
      StackValues lastDupOut = (StackValues) lastDup.outValue();
      StackValue topAfterDups = lastDupOut.getStackValues()[lastDupOut.getStackValues().length - 1];
      StackValue newTopAfterDups = topAfterDups.duplicate(topAfterDups.getHeight());
      lastDupOut.getStackValues()[lastDupOut.getStackValues().length - 1] = newTopAfterDups;
      newTopAfterDups.definition = lastDup;
      StackValue newStoreSrc = oldStoreSrc.duplicate(topAfterDups.getHeight() + 1);
      Dup newDup = new Dup(topAfterDups, newStoreSrc, newTopAfterDups);
      newDup.setPosition(lastDup.getPosition());
      PeepholeHelper.resetPrevious(it, dups.size());
      it.add(newDup);
      Store newStore = new Store(storeOut, newStoreSrc);
      newStore.setPosition(lastDup.getPosition());
      it.add(newStore);
    }
    return true;
  }

  @Override
  public boolean resetAfterMatch() {
    return false;
  }
}
