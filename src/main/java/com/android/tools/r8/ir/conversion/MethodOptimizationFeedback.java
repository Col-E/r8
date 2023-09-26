// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
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
import com.android.tools.r8.utils.BitSetUtils;
import java.util.BitSet;
import java.util.Set;

public interface MethodOptimizationFeedback {

  void markForceInline(DexEncodedMethod method);

  void markInlinedIntoSingleCallSite(DexEncodedMethod method);

  void markMethodCannotBeKept(DexEncodedMethod method);

  void methodInitializesClassesOnNormalExit(
      DexEncodedMethod method, Set<DexType> initializedClasses);

  void methodReturnsArgument(DexEncodedMethod method, int argument);

  void methodReturnsAbstractValue(
      DexEncodedMethod method, AppView<AppInfoWithLiveness> appView, AbstractValue abstractValue);

  default void setDynamicReturnType(
      ProgramMethod method, AppView<?> appView, DynamicType dynamicType) {
    setDynamicReturnType(method.getDefinition(), appView, dynamicType);
  }

  void setDynamicReturnType(DexEncodedMethod method, AppView<?> appView, DynamicType dynamicType);

  void methodMayNotHaveSideEffects(DexEncodedMethod method);

  void methodReturnValueOnlyDependsOnArguments(DexEncodedMethod method);

  void methodNeverReturnsNormally(ProgramMethod method);

  void markProcessed(DexEncodedMethod method, ConstraintWithTarget state);

  void markAsPropagated(DexEncodedMethod method);

  void setBridgeInfo(DexEncodedMethod method, BridgeInfo bridgeInfo);

  void setClassInlinerMethodConstraint(
      ProgramMethod method, ClassInlinerMethodConstraint classInlinerConstraint);

  void setEnumUnboxerMethodClassification(
      ProgramMethod method, EnumUnboxerMethodClassification enumUnboxerMethodClassification);

  void setInstanceInitializerInfoCollection(
      DexEncodedMethod method, InstanceInitializerInfoCollection instanceInitializerInfoCollection);

  void setInitializerEnablingJavaVmAssertions(DexEncodedMethod method);

  void setNonNullParamOrThrow(DexEncodedMethod method, BitSet facts);

  void setNonNullParamOnNormalExits(DexEncodedMethod method, BitSet facts);

  void setSimpleInliningConstraint(ProgramMethod method, SimpleInliningConstraint constraint);

  void classInitializerMayBePostponed(DexEncodedMethod method);

  void setParametersWithBitwiseOperations(
      ProgramMethod method, BitSet parametersWithBitwiseOperations);

  void setUnusedArguments(ProgramMethod method, BitSet unusedArguments);

  // Unset methods.

  void unsetAbstractReturnValue(ProgramMethod method);

  void unsetBridgeInfo(DexEncodedMethod method);

  void unsetClassInitializerMayBePostponed(ProgramMethod method);

  void unsetClassInlinerMethodConstraint(ProgramMethod method);

  void unsetDynamicReturnType(ProgramMethod method);

  void unsetEnumUnboxerMethodClassification(ProgramMethod method);

  void unsetForceInline(ProgramMethod method);

  void unsetInitializedClassesOnNormalExit(ProgramMethod method);

  void unsetInitializerEnablingJavaVmAssertions(ProgramMethod method);

  void unsetInlinedIntoSingleCallSite(ProgramMethod method);

  void unsetInstanceInitializerInfoCollection(ProgramMethod method);

  void unsetMayNotHaveSideEffects(ProgramMethod method);

  void unsetNeverReturnsNormally(ProgramMethod method);

  void unsetNonNullParamOnNormalExits(ProgramMethod method);

  void unsetNonNullParamOrThrow(ProgramMethod method);

  void unsetReturnedArgument(ProgramMethod method);

  void unsetReturnValueOnlyDependsOnArguments(ProgramMethod method);

  void unsetSimpleInliningConstraint(ProgramMethod method);

  void unsetUnusedArguments(ProgramMethod method);

  default void unsetOptimizationInfoForAbstractMethod(ProgramMethod method) {
    if (method.getOptimizationInfo().isMutableOptimizationInfo()) {
      unsetAbstractReturnValue(method);
      unsetBridgeInfo(method.getDefinition());
      unsetClassInitializerMayBePostponed(method);
      unsetClassInlinerMethodConstraint(method);
      unsetDynamicReturnType(method);
      unsetEnumUnboxerMethodClassification(method);
      unsetForceInline(method);
      unsetInitializedClassesOnNormalExit(method);
      unsetInitializerEnablingJavaVmAssertions(method);
      unsetInstanceInitializerInfoCollection(method);
      unsetMayNotHaveSideEffects(method);
      unsetNeverReturnsNormally(method);
      unsetNonNullParamOnNormalExits(method);
      unsetNonNullParamOrThrow(method);
      unsetReturnedArgument(method);
      unsetReturnValueOnlyDependsOnArguments(method);
      unsetSimpleInliningConstraint(method);
      unsetUnusedArguments(method);
    }
  }

  default void unsetOptimizationInfoForThrowNullMethod(AppView<?> appView, ProgramMethod method) {
    if (!appView.hasClassHierarchy() || appView.getKeepInfo(method).isPinned(appView.options())) {
      assert method.getOptimizationInfo().isDefault();
    } else {
      unsetOptimizationInfoForAbstractMethod(method);
      methodNeverReturnsNormally(method);
      setUnusedArguments(
          method, BitSetUtils.createFilled(true, method.getDefinition().getNumberOfArguments()));
    }
  }
}
