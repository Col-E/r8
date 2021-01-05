// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.ObjectsMethods;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.ValueUtils;
import java.util.Set;

public class ObjectsMethodOptimizer extends StatelessLibraryMethodModelCollection {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final ObjectsMethods objectsMethods;

  ObjectsMethodOptimizer(AppView<?> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    this.appView = appView;
    this.dexItemFactory = dexItemFactory;
    this.objectsMethods = dexItemFactory.objectsMethods;
  }

  @Override
  public DexType getType() {
    return dexItemFactory.objectsType;
  }

  @Override
  public void optimize(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues) {
    if (objectsMethods.isRequireNonNullMethod(singleTarget.getReference())) {
      optimizeRequireNonNull(instructionIterator, invoke, affectedValues);
    } else if (singleTarget.getReference() == objectsMethods.toStringWithObject) {
      optimizeToStringWithObject(code, instructionIterator, invoke, affectedValues);
    }
  }

  private void optimizeRequireNonNull(
      InstructionListIterator instructionIterator, InvokeMethod invoke, Set<Value> affectedValues) {
    Value inValue = invoke.getFirstArgument();
    if (inValue.getType().isDefinitelyNotNull()) {
      Value outValue = invoke.outValue();
      if (outValue != null) {
        affectedValues.addAll(outValue.affectedValues());
        outValue.replaceUsers(inValue);
      }
      // TODO(b/152853271): Debugging information is lost here (DebugLocalWrite may be required).
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private void optimizeToStringWithObject(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      Set<Value> affectedValues) {
    // Optimize Objects.toString(null) into "null".
    Value object = invoke.getFirstArgument();
    if (object.getType().isDefinitelyNull()) {
      instructionIterator.replaceCurrentInstructionWithConstString(appView, code, "null");
      if (invoke.hasOutValue()) {
        affectedValues.addAll(invoke.outValue().affectedValues());
      }
      return;
    }

    // Remove Objects.toString(stringBuilder) if unused.
    if (ValueUtils.isStringBuilder(invoke.getFirstArgument(), dexItemFactory)) {
      if (!invoke.hasOutValue() || !invoke.outValue().hasNonDebugUsers()) {
        instructionIterator.removeOrReplaceByDebugLocalRead();
      }
    }
  }
}
