// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Value;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

public class IR2LIRConverter {

  private IR2LIRConverter() {}

  public static LIRCode translate(IRCode irCode) {
    Reference2IntMap<Value> values = new Reference2IntOpenHashMap<>();
    int index = 0;
    for (Instruction instruction : irCode.instructions()) {
      if (instruction.hasOutValue()) {
        values.put(instruction.outValue(), index);
      }
      index++;
    }
    LIRBuilder<Value> builder =
        new LIRBuilder<Value>(irCode.context().getReference(), values::getInt)
            .setMetadata(irCode.metadata());
    BasicBlockIterator blockIt = irCode.listIterator();
    while (blockIt.hasNext()) {
      BasicBlock block = blockIt.next();
      // TODO(b/225838009): Support control flow.
      assert !block.hasPhis();
      InstructionIterator it = block.iterator();
      while (it.hasNext()) {
        Instruction instruction = it.next();
        builder.setCurrentPosition(instruction.getPosition());
        instruction.buildLIR(builder);
      }
    }
    return builder.build();
  }
}
