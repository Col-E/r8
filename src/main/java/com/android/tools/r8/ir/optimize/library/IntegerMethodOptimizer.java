// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleBoxedIntegerValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import java.util.Set;

public class IntegerMethodOptimizer extends StatelessLibraryMethodModelCollection {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;

  IntegerMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  @Override
  public DexType getType() {
    return dexItemFactory.boxedIntType;
  }

  @Override
  public void optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove) {
    if (singleTarget.getReference().isIdenticalTo(dexItemFactory.integerMembers.intValue)) {
      optimizeIntegerValue(code, instructionIterator, invoke);
    }
  }

  private void optimizeIntegerValue(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod intValueInvoke) {
    // Optimize Integer.valueOf(i).intValue() into i.
    AbstractValue abstractValue =
        intValueInvoke.getFirstArgument().getAbstractValue(appView, code.context());
    if (abstractValue.isSingleBoxedInteger()) {
      if (intValueInvoke.hasOutValue()) {
        SingleBoxedIntegerValue singleBoxedInteger = abstractValue.asSingleBoxedInteger();
        instructionIterator.replaceCurrentInstruction(
            singleBoxedInteger
                .toPrimitive(appView.abstractValueFactory())
                .createMaterializingInstruction(appView, code, intValueInvoke));
      } else {
        instructionIterator.removeOrReplaceByDebugLocalRead();
      }
    }
  }
}
