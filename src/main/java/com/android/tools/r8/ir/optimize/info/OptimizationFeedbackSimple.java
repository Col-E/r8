// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
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
import com.android.tools.r8.utils.OptionalBool;
import java.util.BitSet;
import java.util.Set;
import java.util.function.Consumer;

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
  public void markFieldHasDynamicType(DexEncodedField field, DynamicType dynamicType) {
    field.getMutableOptimizationInfo().setDynamicType(dynamicType);
  }

  @Override
  public void markFieldBitsRead(DexEncodedField field, int bitsRead) {
    // Ignored.
  }

  @Override
  public void recordFieldHasAbstractValue(
      DexEncodedField field, AppView<AppInfoWithLiveness> appView, AbstractValue abstractValue) {
    if (appView.appInfo().mayPropagateValueFor(appView, field.getReference())) {
      field.getMutableOptimizationInfo().setAbstractValue(abstractValue);
    }
  }

  public void setMultiCallerMethod(ProgramMethod method) {
    method.getDefinition().getMutableOptimizationInfo().setMultiCallerMethod();
  }

  // METHOD OPTIMIZATION INFO.

  public void joinMaxRemovedAndroidLogLevel(ProgramMethod method, int maxRemovedAndroidLogLevel) {
    method
        .getDefinition()
        .getMutableOptimizationInfo()
        .joinMaxRemovedAndroidLogLevel(maxRemovedAndroidLogLevel);
  }

  @Override
  public void markForceInline(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void markInlinedIntoSingleCallSite(DexEncodedMethod method) {
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
  public void setDynamicReturnType(
      DexEncodedMethod method, AppView<?> appView, DynamicType dynamicType) {
    method.getMutableOptimizationInfo().setDynamicType(appView, dynamicType, method);
  }

  @Override
  public void methodMayNotHaveSideEffects(DexEncodedMethod method) {
    method.getMutableOptimizationInfo().markMayNotHaveSideEffects();
  }

  @Override
  public void methodReturnValueOnlyDependsOnArguments(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void methodNeverReturnsNormally(ProgramMethod method) {
    method.getDefinition().getMutableOptimizationInfo().markNeverReturnsNormally();
  }

  @Override
  public void markAsPropagated(DexEncodedMethod method) {
    method.getMutableOptimizationInfo().markAsPropagated();
  }

  @Override
  public void markProcessed(DexEncodedMethod method, ConstraintWithTarget state) {
    method.markProcessed(state);
  }

  public void setArgumentInfos(ProgramMethod method, CallSiteOptimizationInfo argumentInfos) {
    DexEncodedMethod definition = method.getDefinition();
    definition.getMutableOptimizationInfo().setArgumentInfos(definition, argumentInfos);
  }

  @Override
  public void setBridgeInfo(DexEncodedMethod method, BridgeInfo bridgeInfo) {
    method.getMutableOptimizationInfo().setBridgeInfo(bridgeInfo);
  }

  @Override
  public void setClassInlinerMethodConstraint(
      ProgramMethod method, ClassInlinerMethodConstraint classInlinerConstraint) {
    // Ignored.
  }

  public void setConvertCheckNotNull(DexClassAndMethod method) {
    method.getDefinition().getMutableOptimizationInfo().setConvertCheckNotNull();
  }

  @Override
  public void setEnumUnboxerMethodClassification(
      ProgramMethod method, EnumUnboxerMethodClassification enumUnboxerMethodClassification) {
    method
        .getDefinition()
        .getMutableOptimizationInfo()
        .setEnumUnboxerMethodClassification(enumUnboxerMethodClassification);
  }

  @Override
  public void setInstanceInitializerInfoCollection(
      DexEncodedMethod method,
      InstanceInitializerInfoCollection instanceInitializerInfoCollection) {
    method
        .getMutableOptimizationInfo()
        .setInstanceInitializerInfoCollection(instanceInitializerInfoCollection);
  }

  @Override
  public void setInitializerEnablingJavaVmAssertions(DexEncodedMethod method) {
    method.getMutableOptimizationInfo().setInitializerEnablingJavaAssertions();
  }

  public void setIsReturnValueUsed(OptionalBool isReturnValueUsed, ProgramMethod method) {
    method.getDefinition().getMutableOptimizationInfo().setIsReturnValueUsed(isReturnValueUsed);
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
  public void setSimpleInliningConstraint(
      ProgramMethod method, SimpleInliningConstraint constraint) {
    method.getDefinition().getMutableOptimizationInfo().setSimpleInliningConstraint(constraint);
  }

  @Override
  public void classInitializerMayBePostponed(DexEncodedMethod method) {
    method.getMutableOptimizationInfo().markClassInitializerMayBePostponed();
  }

  @Override
  public void setParametersWithBitwiseOperations(
      ProgramMethod method, BitSet parametersWithBitwiseOperations) {
    method
        .getDefinition()
        .getMutableOptimizationInfo()
        .setParametersWithBitwiseOperations(parametersWithBitwiseOperations);
  }

  @Override
  public void setUnusedArguments(ProgramMethod method, BitSet unusedArguments) {
    method.getDefinition().getMutableOptimizationInfo().setUnusedArguments(unusedArguments);
  }

  public void fixupUnusedArguments(ProgramMethod method, Consumer<BitSet> fixer) {
    if (method.getOptimizationInfo().hasUnusedArguments()) {
      MutableMethodOptimizationInfo optimizationInfo =
          method.getDefinition().getMutableOptimizationInfo();
      BitSet newUnusedArguments = (BitSet) optimizationInfo.getUnusedArguments().clone();
      fixer.accept(newUnusedArguments);
      optimizationInfo.fixupUnusedArguments(newUnusedArguments);
    }
  }

  // Unset methods.

  @Override
  public void unsetAbstractReturnValue(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetAbstractReturnValue);
  }

  @Override
  public void unsetBridgeInfo(DexEncodedMethod method) {
    withMutableMethodOptimizationInfo(method, MutableMethodOptimizationInfo::unsetBridgeInfo);
  }

  @Override
  public void unsetClassInitializerMayBePostponed(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetClassInitializerMayBePostponed);
  }

  @Override
  public void unsetClassInlinerMethodConstraint(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetClassInlinerMethodConstraint);
  }

  @Override
  public void unsetDynamicReturnType(ProgramMethod method) {
    withMutableMethodOptimizationInfo(method, MutableMethodOptimizationInfo::unsetDynamicType);
  }

  @Override
  public void unsetEnumUnboxerMethodClassification(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetEnumUnboxerMethodClassification);
  }

  @Override
  public void unsetForceInline(ProgramMethod method) {
    withMutableMethodOptimizationInfo(method, MutableMethodOptimizationInfo::unsetForceInline);
  }

  @Override
  public void unsetInitializedClassesOnNormalExit(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetInitializedClassesOnNormalExit);
  }

  @Override
  public void unsetInitializerEnablingJavaVmAssertions(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetInitializerEnablingJavaVmAssertions);
  }

  @Override
  public void unsetInlinedIntoSingleCallSite(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetInlinedIntoSingleCallSite);
  }

  @Override
  public void unsetInstanceInitializerInfoCollection(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetInstanceInitializerInfoCollection);
  }

  @Override
  public void unsetMayNotHaveSideEffects(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetMayNotHaveSideEffects);
  }

  @Override
  public void unsetNeverReturnsNormally(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetNeverReturnsNormally);
  }

  @Override
  public void unsetNonNullParamOnNormalExits(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetNonNullParamOnNormalExits);
  }

  @Override
  public void unsetNonNullParamOrThrow(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetNonNullParamOrThrow);
  }

  @Override
  public void unsetReturnedArgument(ProgramMethod method) {
    withMutableMethodOptimizationInfo(method, MutableMethodOptimizationInfo::unsetReturnedArgument);
  }

  @Override
  public void unsetReturnValueOnlyDependsOnArguments(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetReturnValueOnlyDependsOnArguments);
  }

  @Override
  public void unsetSimpleInliningConstraint(ProgramMethod method) {
    withMutableMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetSimpleInliningConstraint);
  }

  @Override
  public void unsetUnusedArguments(ProgramMethod method) {
    withMutableMethodOptimizationInfo(method, MutableMethodOptimizationInfo::unsetUnusedArguments);
  }

  private void withMutableMethodOptimizationInfo(
      ProgramMethod method, Consumer<MutableMethodOptimizationInfo> consumer) {
    if (method.getOptimizationInfo().isMutableOptimizationInfo()) {
      consumer.accept(method.getOptimizationInfo().asMutableMethodOptimizationInfo());
    }
  }

  @Deprecated
  private void withMutableMethodOptimizationInfo(
      DexEncodedMethod method, Consumer<MutableMethodOptimizationInfo> consumer) {
    if (method.getOptimizationInfo().isMutableOptimizationInfo()) {
      consumer.accept(method.getOptimizationInfo().asMutableMethodOptimizationInfo());
    }
  }
}
