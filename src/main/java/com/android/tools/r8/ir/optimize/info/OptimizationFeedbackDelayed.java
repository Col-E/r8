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
import com.android.tools.r8.shaking.AppInfoWithLivenessModifier;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class OptimizationFeedbackDelayed extends OptimizationFeedback {

  // Caching of updated optimization info and processed status.
  private final AppInfoWithLivenessModifier appInfoWithLivenessModifier =
      AppInfoWithLiveness.modifier();
  private final Map<DexEncodedField, MutableFieldOptimizationInfo> fieldOptimizationInfos =
      new IdentityHashMap<>();
  private final Map<DexEncodedMethod, MutableMethodOptimizationInfo> methodOptimizationInfos =
      new IdentityHashMap<>();
  private final Map<DexEncodedMethod, ConstraintWithTarget> processed = new IdentityHashMap<>();

  private synchronized MutableFieldOptimizationInfo getFieldOptimizationInfoForUpdating(
      DexEncodedField field) {
    MutableFieldOptimizationInfo info = fieldOptimizationInfos.get(field);
    if (info != null) {
      return info;
    }
    info = field.getOptimizationInfo().toMutableOptimizationInfo().mutableCopy();
    fieldOptimizationInfos.put(field, info);
    return info;
  }

  private synchronized MutableMethodOptimizationInfo getMethodOptimizationInfoForUpdating(
      DexEncodedMethod method) {
    MutableMethodOptimizationInfo info = methodOptimizationInfos.get(method);
    if (info != null) {
      return info;
    }
    info = method.getOptimizationInfo().toMutableOptimizationInfo().mutableCopy();
    methodOptimizationInfos.put(method, info);
    return info;
  }

  private MutableMethodOptimizationInfo getMethodOptimizationInfoForUpdating(ProgramMethod method) {
    return getMethodOptimizationInfoForUpdating(method.getDefinition());
  }

  @Override
  public void fixupOptimizationInfos(
      AppView<?> appView, ExecutorService executorService, OptimizationInfoFixer fixer)
      throws ExecutionException {
    updateVisibleOptimizationInfo();
    super.fixupOptimizationInfos(appView, executorService, fixer);
  }

  @Override
  public void modifyAppInfoWithLiveness(Consumer<AppInfoWithLivenessModifier> consumer) {
    consumer.accept(appInfoWithLivenessModifier);
  }

  public void refineAppInfoWithLiveness(AppInfoWithLiveness appInfo) {
    appInfoWithLivenessModifier.modify(appInfo);
  }

  public void updateVisibleOptimizationInfo() {
    // Remove methods that have become obsolete. A method may become obsolete, for example, as a
    // result of the class staticizer, which aims to transform virtual methods on companion classes
    // into static methods on the enclosing class of the companion class.
    IteratorUtils.removeIf(
        methodOptimizationInfos.entrySet().iterator(), entry -> entry.getKey().isObsolete());
    IteratorUtils.removeIf(processed.entrySet().iterator(), entry -> entry.getKey().isObsolete());

    // Update field optimization info.
    fieldOptimizationInfos.forEach(DexEncodedField::setOptimizationInfo);
    fieldOptimizationInfos.clear();

    // Update method optimization info.
    methodOptimizationInfos.forEach(DexEncodedMethod::setOptimizationInfo);
    methodOptimizationInfos.clear();

    // Mark the processed methods as processed.
    processed.forEach(DexEncodedMethod::markProcessed);
    processed.clear();
  }

  public boolean noUpdatesLeft() {
    assert appInfoWithLivenessModifier.isEmpty();
    assert fieldOptimizationInfos.isEmpty()
        : StringUtils.join(", ", fieldOptimizationInfos.keySet());
    assert methodOptimizationInfos.isEmpty()
        : StringUtils.join(", ", methodOptimizationInfos.keySet());
    assert processed.isEmpty() : StringUtils.join(", ", processed.keySet());
    return true;
  }

  // FIELD OPTIMIZATION INFO:

  @Override
  public void markFieldCannotBeKept(DexEncodedField field) {
    getFieldOptimizationInfoForUpdating(field).cannotBeKept();
  }

  @Override
  public void markFieldAsDead(DexEncodedField field) {
    getFieldOptimizationInfoForUpdating(field).markAsDead();
  }

  @Override
  public void markFieldAsPropagated(DexEncodedField field) {
    getFieldOptimizationInfoForUpdating(field).markAsPropagated();
  }

  @Override
  public void markFieldHasDynamicType(DexEncodedField field, DynamicType dynamicType) {
    getFieldOptimizationInfoForUpdating(field).setDynamicType(dynamicType);
  }

  @Override
  public void markFieldBitsRead(DexEncodedField field, int bitsRead) {
    getFieldOptimizationInfoForUpdating(field).joinReadBits(bitsRead);
  }

  @Override
  public void recordFieldHasAbstractValue(
      DexEncodedField field, AppView<AppInfoWithLiveness> appView, AbstractValue abstractValue) {
    assert appView.appInfo().getFieldAccessInfoCollection().contains(field.getReference());
    assert !appView
        .appInfo()
        .getFieldAccessInfoCollection()
        .get(field.getReference())
        .hasReflectiveAccess();
    if (appView.appInfo().mayPropagateValueFor(appView, field.getReference())) {
      getFieldOptimizationInfoForUpdating(field).setAbstractValue(abstractValue);
    }
  }

  // METHOD OPTIMIZATION INFO:

  @Override
  public void markForceInline(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markForceInline();
  }

  @Override
  public synchronized void markInlinedIntoSingleCallSite(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markInlinedIntoSingleCallSite();
  }

  @Override
  public void markMethodCannotBeKept(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).cannotBeKept();
  }

  @Override
  public synchronized void methodInitializesClassesOnNormalExit(
      DexEncodedMethod method, Set<DexType> initializedClasses) {
    getMethodOptimizationInfoForUpdating(method)
        .markInitializesClassesOnNormalExit(initializedClasses);
  }

  @Override
  public synchronized void methodReturnsArgument(DexEncodedMethod method, int argument) {
    getMethodOptimizationInfoForUpdating(method).markReturnsArgument(argument);
  }

  @Override
  public synchronized void methodReturnsAbstractValue(
      DexEncodedMethod method, AppView<AppInfoWithLiveness> appView, AbstractValue value) {
    if (appView.appInfo().mayPropagateValueFor(appView, method.getReference())) {
      getMethodOptimizationInfoForUpdating(method).markReturnsAbstractValue(value);
    }
  }

  @Override
  public synchronized void setDynamicReturnType(
      DexEncodedMethod method, AppView<?> appView, DynamicType dynamicType) {
    getMethodOptimizationInfoForUpdating(method).setDynamicType(appView, dynamicType, method);
  }

  @Override
  public synchronized void methodNeverReturnsNormally(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).markNeverReturnsNormally();
  }

  @Override
  public synchronized void methodMayNotHaveSideEffects(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markMayNotHaveSideEffects();
  }

  @Override
  public synchronized void methodReturnValueOnlyDependsOnArguments(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markReturnValueOnlyDependsOnArguments();
  }

  @Override
  public synchronized void markAsPropagated(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markAsPropagated();
  }

  @Override
  public synchronized void markProcessed(DexEncodedMethod method, ConstraintWithTarget state) {
    processed.put(method, state);
  }

  @Override
  public synchronized void setBridgeInfo(DexEncodedMethod method, BridgeInfo bridgeInfo) {
    getMethodOptimizationInfoForUpdating(method).setBridgeInfo(bridgeInfo);
  }

  @Override
  public synchronized void setClassInlinerMethodConstraint(
      ProgramMethod method, ClassInlinerMethodConstraint classInlinerConstraint) {
    getMethodOptimizationInfoForUpdating(method)
        .setClassInlinerMethodConstraint(classInlinerConstraint);
  }

  @Override
  public synchronized void setEnumUnboxerMethodClassification(
      ProgramMethod method, EnumUnboxerMethodClassification enumUnboxerMethodClassification) {
    getMethodOptimizationInfoForUpdating(method)
        .setEnumUnboxerMethodClassification(enumUnboxerMethodClassification);
  }

  @Override
  public synchronized void setInstanceInitializerInfoCollection(
      DexEncodedMethod method,
      InstanceInitializerInfoCollection instanceInitializerInfoCollection) {
    getMethodOptimizationInfoForUpdating(method)
        .setInstanceInitializerInfoCollection(instanceInitializerInfoCollection);
  }

  @Override
  public synchronized void setInitializerEnablingJavaVmAssertions(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).setInitializerEnablingJavaAssertions();
  }

  @Override
  public synchronized void setNonNullParamOrThrow(DexEncodedMethod method, BitSet facts) {
    getMethodOptimizationInfoForUpdating(method).setNonNullParamOrThrow(facts);
  }

  @Override
  public synchronized void setNonNullParamOnNormalExits(DexEncodedMethod method, BitSet facts) {
    getMethodOptimizationInfoForUpdating(method).setNonNullParamOnNormalExits(facts);
  }

  @Override
  public synchronized void setSimpleInliningConstraint(
      ProgramMethod method, SimpleInliningConstraint constraint) {
    getMethodOptimizationInfoForUpdating(method).setSimpleInliningConstraint(constraint);
  }

  @Override
  public synchronized void classInitializerMayBePostponed(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markClassInitializerMayBePostponed();
  }

  @Override
  public void setParametersWithBitwiseOperations(
      ProgramMethod method, BitSet parametersWithBitwiseOperations) {
    getMethodOptimizationInfoForUpdating(method)
        .setParametersWithBitwiseOperations(parametersWithBitwiseOperations);
  }

  @Override
  public synchronized void setUnusedArguments(ProgramMethod method, BitSet unusedArguments) {
    getMethodOptimizationInfoForUpdating(method).setUnusedArguments(unusedArguments);
  }

  // Unset methods.

  @Override
  public synchronized void unsetAbstractReturnValue(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetAbstractReturnValue();
  }

  @Override
  public synchronized void unsetBridgeInfo(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetBridgeInfo();
  }

  @Override
  public synchronized void unsetClassInitializerMayBePostponed(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetClassInitializerMayBePostponed();
  }

  @Override
  public synchronized void unsetClassInlinerMethodConstraint(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetClassInlinerMethodConstraint();
  }

  @Override
  public synchronized void unsetDynamicReturnType(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetDynamicType();
  }

  @Override
  public synchronized void unsetEnumUnboxerMethodClassification(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetEnumUnboxerMethodClassification();
  }

  @Override
  public synchronized void unsetForceInline(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetForceInline();
  }

  @Override
  public synchronized void unsetInitializedClassesOnNormalExit(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetInitializedClassesOnNormalExit();
  }

  @Override
  public synchronized void unsetInitializerEnablingJavaVmAssertions(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetInitializerEnablingJavaVmAssertions();
  }

  @Override
  public synchronized void unsetInlinedIntoSingleCallSite(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetInlinedIntoSingleCallSite();
  }

  @Override
  public synchronized void unsetInstanceInitializerInfoCollection(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetInstanceInitializerInfoCollection();
  }

  @Override
  public synchronized void unsetMayNotHaveSideEffects(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetMayNotHaveSideEffects();
  }

  @Override
  public synchronized void unsetNeverReturnsNormally(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetNeverReturnsNormally();
  }

  @Override
  public synchronized void unsetNonNullParamOnNormalExits(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetNonNullParamOnNormalExits();
  }

  @Override
  public synchronized void unsetNonNullParamOrThrow(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetNonNullParamOrThrow();
  }

  @Override
  public synchronized void unsetReturnedArgument(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetReturnedArgument();
  }

  @Override
  public synchronized void unsetReturnValueOnlyDependsOnArguments(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetReturnValueOnlyDependsOnArguments();
  }

  @Override
  public synchronized void unsetSimpleInliningConstraint(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetSimpleInliningConstraint();
  }

  @Override
  public synchronized void unsetUnusedArguments(ProgramMethod method) {
    getMethodOptimizationInfoForUpdating(method).unsetUnusedArguments();
  }
}
