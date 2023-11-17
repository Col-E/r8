// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.Set;

public class ObjectMethodOptimizer extends StatelessLibraryMethodModelCollection {

  private final DexItemFactory dexItemFactory;

  ObjectMethodOptimizer(AppView<?> appView) {
    this.dexItemFactory = appView.dexItemFactory();
  }

  @Override
  public DexType getType() {
    return dexItemFactory.objectType;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public InstructionListIterator optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      AffectedValues affectedValues,
      Set<BasicBlock> blocksToRemove) {
    if (singleTarget.getReference() == dexItemFactory.objectMembers.getClass) {
      optimizeGetClass(instructionIterator, invoke);
    }
    return instructionIterator;
  }

  private void optimizeGetClass(InstructionListIterator instructionIterator, InvokeMethod invoke) {
    if ((!invoke.hasOutValue() || !invoke.outValue().hasAnyUsers())
        && invoke.inValues().get(0).isNeverNull()) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }
}
