// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import java.util.Set;

public class EnumMethodOptimizer implements LibraryMethodModelCollection {
  private final AppView<?> appView;

  EnumMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
  }

  @Override
  public DexType getType() {
    return appView.dexItemFactory().enumType;
  }

  @Override
  public void optimize(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues) {
    if (singleTarget.getReference() == appView.dexItemFactory().enumMembers.valueOf
        && invoke.inValues().get(0).isConstClass()) {
      insertAssumeDynamicType(code, instructionIterator, invoke);
    }
  }

  private void insertAssumeDynamicType(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    // TODO(b/152516470): Support unboxing enums with Enum#valueOf in try-catch.
    if (invoke.getBlock().hasCatchHandlers()) {
      return;
    }
    DexType enumType = invoke.inValues().get(0).getConstInstruction().asConstClass().getValue();
    DexProgramClass enumClass = appView.definitionForProgramType(enumType);
    if (enumClass == null
        || !enumClass.isEnum()
        || enumClass.superType != appView.dexItemFactory().enumType) {
      return;
    }
    TypeElement dynamicUpperBoundType =
        TypeElement.fromDexType(enumType, definitelyNotNull(), appView);
    Value outValue = invoke.outValue();
    if (outValue == null) {
      return;
    }
    // Replace usages of out-value by the out-value of the AssumeDynamicType instruction.
    Value specializedOutValue = code.createValue(outValue.getType(), outValue.getLocalInfo());
    outValue.replaceUsers(specializedOutValue);

    // Insert AssumeDynamicType instruction.
    Assume assumeInstruction =
        Assume.createAssumeDynamicTypeInstruction(
            dynamicUpperBoundType, null, specializedOutValue, outValue, invoke, appView);
    assumeInstruction.setPosition(appView.options().debug ? invoke.getPosition() : Position.none());
    instructionIterator.add(assumeInstruction);
  }
}
