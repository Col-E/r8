// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import java.util.Set;

public class ByteMethodOptimizer extends StatelessLibraryMethodModelCollection {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;

  ByteMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  @Override
  public DexType getType() {
    return dexItemFactory.boxedByteType;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove) {
    if (singleTarget.getReference() == dexItemFactory.byteMembers.byteValue) {
      optimizeByteValue(instructionIterator, invoke);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void optimizeByteValue(
      InstructionListIterator instructionIterator, InvokeMethod byteValueInvoke) {
    // Optimize Byte.valueOf(b).byteValue() into b.
    Value argument = byteValueInvoke.getFirstArgument().getAliasedValue();
    if (!argument.isPhi()) {
      Instruction definition = argument.getDefinition();
      if (definition.isInvokeStatic()
          && definition.asInvokeStatic().getInvokedMethod() == dexItemFactory.byteMembers.valueOf) {
        byteValueInvoke.outValue().replaceUsers(definition.asInvokeStatic().getFirstArgument());
        instructionIterator.removeOrReplaceByDebugLocalRead();
      }
    }
  }
}
