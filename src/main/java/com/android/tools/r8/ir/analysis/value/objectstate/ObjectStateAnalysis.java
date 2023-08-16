// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value.objectstate;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldArgumentInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ObjectStateAnalysis {

  public static ObjectState computeObjectState(
      Value value, AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    assert !value.hasAliasedValue();
    if (value.isDefinedByInstructionSatisfying(
        i -> i.isNewArrayEmpty() || i.isNewArrayFilledData() || i.isNewArrayFilled())) {
      return computeNewArrayObjectState(value, appView, context);
    }
    if (value.isDefinedByInstructionSatisfying(Instruction::isNewInstance)) {
      return computeNewInstanceObjectState(value, appView, context);
    }
    return ObjectState.empty();
  }

  private static ObjectState computeNewArrayObjectState(
      Value value, AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    AbstractValue abstractValue = value.definition.getAbstractValue(appView, context);
    if (abstractValue.isStatefulObjectValue()) {
      // TODO(b/204272377): Avoid wrapping and unwrapping the object state.
      return abstractValue.asStatefulObjectValue().getObjectState();
    }
    return ObjectState.empty();
  }

  private static ObjectState computeNewInstanceObjectState(
      Value value, AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    NewInstance newInstance = value.definition.asNewInstance();
    InvokeDirect uniqueConstructorInvoke =
        newInstance.getUniqueConstructorInvoke(appView.dexItemFactory());
    if (uniqueConstructorInvoke == null) {
      return ObjectState.empty();
    }

    DexClassAndMethod singleTarget = uniqueConstructorInvoke.lookupSingleTarget(appView, context);
    if (singleTarget == null) {
      return ObjectState.empty();
    }

    InstanceFieldInitializationInfoCollection initializationInfos =
        singleTarget
            .getDefinition()
            .getOptimizationInfo()
            .getInstanceInitializerInfo(uniqueConstructorInvoke)
            .fieldInitializationInfos();
    if (initializationInfos.isEmpty()) {
      return ObjectState.empty();
    }

    ObjectState.Builder builder = ObjectState.builder();
    initializationInfos.forEach(
        appView,
        (field, initializationInfo) -> {
          // If the instance field is not written only in the instance initializer, then we can't
          // conclude that this field will have a constant value.
          //
          // We have special handling for library fields that satisfy the property that they are
          // only written in their corresponding instance initializers. This is needed since we
          // don't analyze these instance initializers in the Enqueuer, as they are in the library.
          if (!appView.appInfo().isInstanceFieldWrittenOnlyInInstanceInitializers(field)
              && !appView.dexItemFactory().enumMembers.isNameOrOrdinalField(field.getReference())) {
            return;
          }
          if (initializationInfo.isArgumentInitializationInfo()) {
            InstanceFieldArgumentInitializationInfo argumentInitializationInfo =
                initializationInfo.asArgumentInitializationInfo();
            Value argument =
                uniqueConstructorInvoke.getArgument(argumentInitializationInfo.getArgumentIndex());
            builder.recordFieldHasValue(field, argument.getAbstractValue(appView, context));
          } else if (initializationInfo.isSingleValue()) {
            builder.recordFieldHasValue(field, initializationInfo.asSingleValue());
          }
        });
    return builder.build();
  }
}
