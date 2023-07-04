// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.ArrayLength;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import java.util.Set;

public class KnownArrayLengthRewriter extends CodeRewriterPass<AppInfo> {

  public KnownArrayLengthRewriter(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getTimingId() {
    return "KnownArrayLengthRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return code.metadata().mayHaveArrayLength();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction current = iterator.next();
      if (!current.isArrayLength()) {
        continue;
      }

      ArrayLength arrayLength = current.asArrayLength();
      if (arrayLength.hasOutValue() && arrayLength.outValue().hasLocalInfo()) {
        continue;
      }

      Value array = arrayLength.array().getAliasedValue();
      if (array.isPhi() || !arrayLength.array().isNeverNull() || array.hasLocalInfo()) {
        continue;
      }

      AbstractValue abstractValue = array.getAbstractValue(appView, code.context());
      if (!abstractValue.hasKnownArrayLength() && !array.isNeverNull()) {
        continue;
      }
      Instruction arrayDefinition = array.getDefinition();
      assert arrayDefinition != null;

      Set<Phi> phiUsers = arrayLength.outValue().uniquePhiUsers();
      if (arrayDefinition.isNewArrayEmpty()) {
        Value size = arrayDefinition.asNewArrayEmpty().size();
        arrayLength.outValue().replaceUsers(size);
        iterator.removeOrReplaceByDebugLocalRead();
        hasChanged = true;
      } else if (arrayDefinition.isNewArrayFilledData()) {
        long size = arrayDefinition.asNewArrayFilledData().size;
        if (size > Integer.MAX_VALUE) {
          continue;
        }
        iterator.replaceCurrentInstructionWithConstInt(code, (int) size);
        hasChanged = true;
      } else if (abstractValue.hasKnownArrayLength()) {
        iterator.replaceCurrentInstructionWithConstInt(code, abstractValue.getKnownArrayLength());
        hasChanged = true;
      } else {
        continue;
      }

      phiUsers.forEach(Phi::removeTrivialPhi);
    }
    if (hasChanged) {
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }
}
