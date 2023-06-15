// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ClassInlinerMethodConstraint;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.BitSet;
import java.util.Set;

public abstract class OptimizationFeedbackIgnore extends OptimizationFeedback {

  private static final OptimizationFeedbackIgnore INSTANCE = new OptimizationFeedbackIgnore() {};

  protected OptimizationFeedbackIgnore() {}

  public static OptimizationFeedbackIgnore getInstance() {
    return INSTANCE;
  }

  // FIELD OPTIMIZATION INFO:

  @Override
  public void markFieldCannotBeKept(DexEncodedField field) {}

  @Override
  public void markFieldAsDead(DexEncodedField field) {}

  @Override
  public void markFieldAsPropagated(DexEncodedField field) {}

  @Override
  public void markFieldHasDynamicType(DexEncodedField field, DynamicType dynamicType) {}

  @Override
  public void markFieldBitsRead(DexEncodedField field, int bitsRead) {}

  @Override
  public void recordFieldHasAbstractValue(
      DexEncodedField field, AppView<AppInfoWithLiveness> appView, AbstractValue abstractValue) {}

  // METHOD OPTIMIZATION INFO:

  @Override
  public void markForceInline(DexEncodedMethod method) {}

  @Override
  public void markInlinedIntoSingleCallSite(DexEncodedMethod method) {}

  @Override
  public void markMethodCannotBeKept(DexEncodedMethod method) {}

  @Override
  public void methodInitializesClassesOnNormalExit(
      DexEncodedMethod method, Set<DexType> initializedClasses) {}

  @Override
  public void methodReturnsArgument(DexEncodedMethod method, int argument) {}

  @Override
  public void methodReturnsAbstractValue(
      DexEncodedMethod method, AppView<AppInfoWithLiveness> appView, AbstractValue value) {}

  @Override
  public void setDynamicReturnType(
      DexEncodedMethod method, AppView<?> appView, DynamicType dynamicType) {}

  @Override
  public void methodMayNotHaveSideEffects(DexEncodedMethod method) {}

  @Override
  public void methodReturnValueOnlyDependsOnArguments(DexEncodedMethod method) {}

  @Override
  public void methodNeverReturnsNormally(ProgramMethod method) {}

  @Override
  public void markAsPropagated(DexEncodedMethod method) {}

  @Override
  public void markProcessed(DexEncodedMethod method, ConstraintWithTarget state) {}

  @Override
  public void setBridgeInfo(DexEncodedMethod method, BridgeInfo bridgeInfo) {}

  @Override
  public void setClassInlinerMethodConstraint(
      ProgramMethod method, ClassInlinerMethodConstraint classInlinerConstraint) {}

  @Override
  public void setEnumUnboxerMethodClassification(
      ProgramMethod method, EnumUnboxerMethodClassification enumUnboxerMethodClassification) {}

  @Override
  public void setInstanceInitializerInfoCollection(
      DexEncodedMethod method,
      InstanceInitializerInfoCollection instanceInitializerInfoCollection) {}

  @Override
  public void setInitializerEnablingJavaVmAssertions(DexEncodedMethod method) {}

  @Override
  public void setNonNullParamOrThrow(DexEncodedMethod method, BitSet facts) {}

  @Override
  public void setNonNullParamOnNormalExits(DexEncodedMethod method, BitSet facts) {}

  @Override
  public void setSimpleInliningConstraint(
      ProgramMethod method, SimpleInliningConstraint constraint) {}

  @Override
  public void classInitializerMayBePostponed(DexEncodedMethod method) {}

  @Override
  public void setParametersWithBitwiseOperations(
      ProgramMethod method, BitSet parametersWithBitwiseOperations) {}

  @Override
  public void setUnusedArguments(ProgramMethod method, BitSet unusedArguments) {}

  // Unset methods.

  @Override
  public void unsetAbstractReturnValue(ProgramMethod method) {}

  @Override
  public void unsetBridgeInfo(DexEncodedMethod method) {}

  @Override
  public void unsetClassInitializerMayBePostponed(ProgramMethod method) {}

  @Override
  public void unsetClassInlinerMethodConstraint(ProgramMethod method) {}

  @Override
  public void unsetDynamicReturnType(ProgramMethod method) {}

  @Override
  public void unsetEnumUnboxerMethodClassification(ProgramMethod method) {}

  @Override
  public void unsetForceInline(ProgramMethod method) {}

  @Override
  public void unsetInitializedClassesOnNormalExit(ProgramMethod method) {}

  @Override
  public void unsetInitializerEnablingJavaVmAssertions(ProgramMethod method) {}

  @Override
  public void unsetInlinedIntoSingleCallSite(ProgramMethod method) {}

  @Override
  public void unsetInstanceInitializerInfoCollection(ProgramMethod method) {}

  @Override
  public void unsetMayNotHaveSideEffects(ProgramMethod method) {}

  @Override
  public void unsetNeverReturnsNormally(ProgramMethod method) {}

  @Override
  public void unsetNonNullParamOnNormalExits(ProgramMethod method) {}

  @Override
  public void unsetNonNullParamOrThrow(ProgramMethod method) {}

  @Override
  public void unsetReturnedArgument(ProgramMethod method) {}

  @Override
  public void unsetReturnValueOnlyDependsOnArguments(ProgramMethod method) {}

  @Override
  public void unsetSimpleInliningConstraint(ProgramMethod method) {}

  @Override
  public void unsetUnusedArguments(ProgramMethod method) {}
}
