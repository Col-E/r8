// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;

public class EnumMethodOptimizer extends StatelessLibraryMethodModelCollection {

  private final AppView<?> appView;

  EnumMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
  }

  @Override
  public DexType getType() {
    return appView.dexItemFactory().enumType;
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
    if (appView.hasLiveness()
        && singleTarget.getReference() == appView.dexItemFactory().enumMembers.valueOf
        && invoke.inValues().get(0).isConstClass()) {
      insertAssumeDynamicType(
          appView.withLiveness(), code, instructionIterator, invoke, affectedValues);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void insertAssumeDynamicType(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      Set<Value> affectedValues) {
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
    Value outValue = invoke.outValue();
    if (outValue == null) {
      return;
    }
    // Replace usages of out-value by the out-value of the AssumeDynamicType instruction.
    Value specializedOutValue =
        code.createValue(
            outValue.getType().asReferenceType().asMeetWithNotNull(), outValue.getLocalInfo());
    outValue.replaceUsers(specializedOutValue, affectedValues);

    // Insert AssumeDynamicType instruction.
    DynamicTypeWithUpperBound dynamicType = enumType.toDynamicType(appView, definitelyNotNull());
    Assume assumeInstruction =
        Assume.create(dynamicType, specializedOutValue, outValue, invoke, appView, code.context());
    assumeInstruction.setPosition(appView.options().debug ? invoke.getPosition() : Position.none());
    instructionIterator.add(assumeInstruction);
  }
}
