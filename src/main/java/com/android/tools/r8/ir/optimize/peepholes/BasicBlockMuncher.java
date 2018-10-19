// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.peepholes;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class BasicBlockMuncher {

  private final List<BasicBlockPeephole> peepholes =
      ImmutableList.of(
          new MoveLoadUpDupPeephole(), new StoreLoadLoadPeephole(), new StoreLoadPeephole());

  public void optimize(IRCode code, DexItemFactory factory) {
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator(block.getInstructions().size());
      boolean matched = false;
      while (matched || it.hasPrevious()) {
        if (!it.hasPrevious()) {
          matched = false;
          it = block.listIterator(block.getInstructions().size());
        }
        for (BasicBlockPeephole peepHole : peepholes) {
          matched |= peepHole.match(it, factory);
        }
        if (it.hasPrevious()) {
          it.previous();
        }
      }
    }
  }
}
