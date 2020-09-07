// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.classinliner.ClassInlinerEligibilityInfo;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.BitSet;
import java.util.Set;

public class OptimizationFeedbackSimple extends OptimizationFeedback {

  private static OptimizationFeedbackSimple INSTANCE = new OptimizationFeedbackSimple();

  OptimizationFeedbackSimple() {}

  public static OptimizationFeedbackSimple getInstance() {
    return INSTANCE;
  }

  // FIELD OPTIMIZATION INFO.

  @Override
  public void markFieldCannotBeKept(DexEncodedField field) {
    field.getMutableOptimizationInfo().markCannotBeKept();
  }

  @Override
  public void markFieldAsDead(DexEncodedField field) {
    field.getMutableOptimizationInfo().markAsDead();
  }

  @Override
  public void markFieldAsPropagated(DexEncodedField field) {
    field.getMutableOptimizationInfo().markAsPropagated();
  }

  @Override
  public void markFieldHasDynamicLowerBoundType(DexEncodedField field, ClassTypeElement type) {
    // Ignored.
  }

  @Override
  public void markFieldHasDynamicUpperBoundType(DexEncodedField field, TypeElement type) {
    // Ignored.
  }

  @Override
  public void markFieldBitsRead(DexEncodedField field, int bitsRead) {
    // Ignored.
  }

  @Override
  public void recordFieldHasAbstractValue(
      DexEncodedField field, AppView<AppInfoWithLiveness> appView, AbstractValue abstractValue) {
    // Ignored.
  }

  // METHOD OPTIMIZATION INFO.

  @Override
  public void markForceInline(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public synchronized void markInlinedIntoSingleCallSite(DexEncodedMethod method) {
    method.getMutableOptimizationInfo().markInlinedIntoSingleCallSite();
  }

  @Override
  public void markMethodCannotBeKept(DexEncodedMethod method) {
    method.getMutableOptimizationInfo().markCannotBeKept();
  }

  @Override
  public void methodInitializesClassesOnNormalExit(
      DexEncodedMethod method, Set<DexType> initializedClasses) {
    // Ignored.
  }

  @Override
  public void methodReturnsArgument(DexEncodedMethod method, int argument) {
    method.getMutableOptimizationInfo().markReturnsArgument(argument);
  }

  @Override
  public void methodReturnsAbstractValue(
      DexEncodedMethod method, AppView<AppInfoWithLiveness> appView, AbstractValue value) {
    method.getMutableOptimizationInfo().markReturnsAbstractValue(value);
  }

  @Override
  public void unsetAbstractReturnValue(DexEncodedMethod method) {
    method.getMutableOptimizationInfo().unsetAbstractReturnValue();
  }

  @Override
  public void methodReturnsObjectWithUpperBoundType(
      DexEncodedMethod method, AppView<?> appView, TypeElement type) {
    method.getMutableOptimizationInfo().markReturnsObjectWithUpperBoundType(appView, type);
  }

  @Override
  public void methodReturnsObjectWithLowerBoundType(
      DexEncodedMethod method, ClassTypeElement type) {
    // Ignored.
  }

  @Override
  public void methodMayNotHaveSideEffects(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void methodReturnValueOnlyDependsOnArguments(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void methodNeverReturnsNormally(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void markAsPropagated(DexEncodedMethod method) {
    method.getMutableOptimizationInfo().markAsPropagated();
  }

  @Override
  public void markProcessed(DexEncodedMethod method, ConstraintWithTarget state) {
    method.markProcessed(state);
  }

  @Override
  public void markCheckNullReceiverBeforeAnySideEffect(DexEncodedMethod method, boolean mark) {
    // Ignored.
  }

  @Override
  public void markTriggerClassInitBeforeAnySideEffect(DexEncodedMethod method, boolean mark) {
    // Ignored.
  }

  @Override
  public void setBridgeInfo(DexEncodedMethod method, BridgeInfo bridgeInfo) {
    method.getMutableOptimizationInfo().setBridgeInfo(bridgeInfo);
  }

  @Override
  public void setClassInlinerEligibility(
      DexEncodedMethod method, ClassInlinerEligibilityInfo eligibility) {
    // Ignored.
  }

  @Override
  public void setInstanceInitializerInfo(
      DexEncodedMethod method, InstanceInitializerInfo instanceInitializerInfo) {
    method.getMutableOptimizationInfo().setInstanceInitializerInfo(instanceInitializerInfo);
  }

  @Override
  public void setInitializerEnablingJavaVmAssertions(DexEncodedMethod method) {
    method.getMutableOptimizationInfo().setInitializerEnablingJavaAssertions();
  }

  @Override
  public void setParameterUsages(DexEncodedMethod method, ParameterUsagesInfo parameterUsagesInfo) {
    // Ignored.
  }

  @Override
  public void setNonNullParamOrThrow(DexEncodedMethod method, BitSet facts) {
    method.getMutableOptimizationInfo().setNonNullParamOrThrow(facts);
  }

  @Override
  public void setNonNullParamOnNormalExits(DexEncodedMethod method, BitSet facts) {
    method.getMutableOptimizationInfo().setNonNullParamOnNormalExits(facts);
  }

  @Override
  public void classInitializerMayBePostponed(DexEncodedMethod method) {
    // Ignored.
  }
}
