package com.android.tools.r8.ir.optimize.peepholes;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Load;
import com.android.tools.r8.ir.code.Store;
import com.android.tools.r8.ir.optimize.peepholes.PeepholeHelper.PeepholeLayout;
import com.google.common.collect.ImmutableList;

/**
 * Peephole that looks for the following pattern:
 *
 * <pre>
 * Store                v0 <- s0
 * Load                 s0 <- v0
 * </pre>
 *
 * and removes both instructions.
 */
public class StoreLoadPeephole implements BasicBlockPeephole {

  private final PeepholeLayout layout =
      PeepholeHelper.getLayout(ImmutableList.of(Instruction::isStore, Instruction::isLoad));

  @Override
  public boolean match(InstructionListIterator it, DexItemFactory factory) {
    Instruction[] match = layout.test(it);
    if (match == null) {
      return false;
    }
    Store store = match[0].asStore();
    Load load = match[1].asLoad();
    if (load.src() != store.outValue() || store.outValue().numberOfAllUsers() != 1) {
      return false;
    }
    // Set the use of loads out to the source of the store.
    load.outValue().replaceUsers(store.src());
    // Remove all uses and remove the instructions.
    store.src().removeUser(store);
    load.src().removeUser(load);
    it.removeOrReplaceByDebugLocalRead();
    it.next();
    it.removeOrReplaceByDebugLocalRead();
    PeepholeHelper.resetNext(it, 1);
    return true;
  }
}
