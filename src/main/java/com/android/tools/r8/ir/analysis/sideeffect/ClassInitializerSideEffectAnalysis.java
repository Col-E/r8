// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.sideeffect;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.ValueMayDependOnEnvironmentAnalysis;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Instruction.SideEffectAssumption;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.Set;

public class ClassInitializerSideEffectAnalysis {

  public enum ClassInitializerSideEffect {
    SIDE_EFFECTS_THAT_CANNOT_BE_POSTPONED,
    SIDE_EFFECTS_THAT_CAN_BE_POSTPONED,
    NONE;

    public boolean canBePostponed() {
      return this != SIDE_EFFECTS_THAT_CANNOT_BE_POSTPONED;
    }

    public boolean isNone() {
      return this == NONE;
    }
  }

  /**
   * A class initializer cannot be postponed if it writes a static field of another class, or if any
   * non-static-put instructions may have side effects.
   */
  @SuppressWarnings("ReferenceEquality")
  public static ClassInitializerSideEffect classInitializerCanBePostponed(
      AppView<AppInfoWithLiveness> appView, IRCode code) {
    ProgramMethod context = code.context();
    // Will be set to true if the control flow must be independent of the environment in order for
    // this class initializer to be postponeable.
    boolean controlFlowRequiredToBeIndependentOfControlFlow = false;
    // The set of values that must be independent of the environment in order for this class
    // initializer to be postponeable.
    Set<Value> valuesRequiredToBeIndependentOfEnvironment = Sets.newIdentityHashSet();
    for (Instruction instruction : code.instructions()) {
      // Array stores are observable if they mutate a non-local array or if they may throw.
      if (instruction.isArrayPut()) {
        ArrayPut arrayPut = instruction.asArrayPut();
        Value array = arrayPut.array().getAliasedValue();
        if (!array.isDefinedByInstructionSatisfying(Instruction::isCreatingArray)
            || arrayPut.instructionInstanceCanThrow(appView, context)) {
          return ClassInitializerSideEffect.SIDE_EFFECTS_THAT_CANNOT_BE_POSTPONED;
        }
        continue;
      }

      // Array creations are observable if they may throw.
      if (instruction.isNewArrayFilled()
          || instruction.isNewArrayEmpty()
          || instruction.isNewArrayFilledData()) {
        if (instruction.instructionInstanceCanThrow(appView, context)) {
          return ClassInitializerSideEffect.SIDE_EFFECTS_THAT_CANNOT_BE_POSTPONED;
        }
        continue;
      }

      if (instruction.isStaticPut()) {
        StaticPut staticPut = instruction.asStaticPut();
        DexEncodedField field =
            appView.appInfo().resolveField(staticPut.getField()).getResolvedField();
        if (field == null
            || field.getHolderType() != context.getHolderType()
            || instruction.instructionInstanceCanThrow(appView, context)) {
          return ClassInitializerSideEffect.SIDE_EFFECTS_THAT_CANNOT_BE_POSTPONED;
        }
        controlFlowRequiredToBeIndependentOfControlFlow = true;
        valuesRequiredToBeIndependentOfEnvironment.add(staticPut.value());
        continue;
      }

      if (instruction.isInvokeConstructor(appView.dexItemFactory())) {
        if (instruction.instructionMayHaveSideEffects(
            appView, context, SideEffectAssumption.IGNORE_RECEIVER_FIELD_ASSIGNMENTS)) {
          return ClassInitializerSideEffect.SIDE_EFFECTS_THAT_CANNOT_BE_POSTPONED;
        }
        continue;
      }

      // For other instructions, bail out if they may have side effects.
      if (instruction.instructionMayHaveSideEffects(appView, context)) {
        return ClassInitializerSideEffect.SIDE_EFFECTS_THAT_CANNOT_BE_POSTPONED;
      }
    }

    if (controlFlowRequiredToBeIndependentOfControlFlow) {
      if (code.controlFlowMayDependOnEnvironment(valuesRequiredToBeIndependentOfEnvironment::add)) {
        return ClassInitializerSideEffect.SIDE_EFFECTS_THAT_CANNOT_BE_POSTPONED;
      }
    }

    if (!valuesRequiredToBeIndependentOfEnvironment.isEmpty()) {
      ValueMayDependOnEnvironmentAnalysis environmentAnalysis =
          new ValueMayDependOnEnvironmentAnalysis(appView, code);
      if (environmentAnalysis.anyValueMayDependOnEnvironment(
          valuesRequiredToBeIndependentOfEnvironment)) {
        return ClassInitializerSideEffect.SIDE_EFFECTS_THAT_CANNOT_BE_POSTPONED;
      }
      return ClassInitializerSideEffect.SIDE_EFFECTS_THAT_CAN_BE_POSTPONED;
    }
    return ClassInitializerSideEffect.NONE;
  }
}
