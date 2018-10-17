package com.android.tools.r8.ir.optimize.peepholes;

import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Load;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.StackValues;
import com.android.tools.r8.ir.code.Store;
import com.android.tools.r8.ir.code.Swap;
import com.android.tools.r8.ir.optimize.peepholes.PeepholeHelper.PeepholeLayout;
import com.google.common.collect.ImmutableList;

/**
 * {@link StoreLoadLoadPeephole} looks for the following pattern:
 *
 * <pre>
 * Store                v5 <- s0
 * Load                 s0 <- v4
 * Load                 s1 <- v5
 * </pre>
 *
 * and replaces with
 *
 * <pre>
 * Load                 s1 <- v4
 * Swap                 [s0, s1] <- s0, s1
 * </pre>
 *
 * This saves a store and load and removes a use of a local.
 */
public class StoreLoadLoadPeephole implements BasicBlockPeephole {

  private final PeepholeLayout layout =
      PeepholeHelper.getLayout(
          ImmutableList.of(Instruction::isStore, Instruction::isLoad, Instruction::isLoad));

  @Override
  public boolean match(InstructionListIterator it) {
    Instruction[] match = layout.test(it);
    if (match == null) {
      return false;
    }
    Store store = match[0].asStore();
    Load load1 = match[1].asLoad();
    Load load2 = match[2].asLoad();
    if (store.src().hasLocalInfo()) {
      return false;
    }
    if (store.src().getTypeLattice().isWide() || load1.outValue().getTypeLattice().isWide()) {
      return false;
    }
    // Ensure the only user of the store is the load2 consumer.
    if (store.outValue() != load2.src() || store.outValue().numberOfAllUsers() != 1) {
      return false;
    }
    // Create the swap instruction sources.
    StackValue source = (StackValue) store.src();
    StackValue load1Source = (StackValue) load1.outValue();

    store.outValue().removeUser(load2);
    load2.outValue().replaceUsers(source);

    // Remove the first store.
    it.removeOrReplaceByDebugLocalRead();
    it.next();

    // Keep first load.
    it.next();

    // Insert a swap instruction
    StackValue newLoad = load1Source.duplicate(source.getHeight());
    StackValue newSource = source.duplicate(newLoad.getHeight() + 1);
    StackValues dest = new StackValues(ImmutableList.of(newLoad, newSource));
    source.replaceUsers(newSource);
    load1Source.replaceUsers(newLoad);
    it.replaceCurrentInstruction(new Swap(dest, source, load1Source));
    PeepholeHelper.resetNext(it, 2);
    return true;
  }
}
