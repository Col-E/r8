// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ClassInlinerMethodConstraint;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
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

  void unsetAbstractReturnValue(DexEncodedMethod method);

  void methodReturnsObjectWithUpperBoundType(
      DexEncodedMethod method, AppView<?> appView, TypeElement type);

  void methodReturnsObjectWithLowerBoundType(DexEncodedMethod method, ClassTypeElement type);

  void methodMayNotHaveSideEffects(DexEncodedMethod method);

  void methodReturnValueOnlyDependsOnArguments(DexEncodedMethod method);

  void methodNeverReturnsNormally(DexEncodedMethod method);

  void markProcessed(DexEncodedMethod method, ConstraintWithTarget state);

  void markAsPropagated(DexEncodedMethod method);

  void markCheckNullReceiverBeforeAnySideEffect(DexEncodedMethod method, boolean mark);

  void markTriggerClassInitBeforeAnySideEffect(DexEncodedMethod method, boolean mark);

  void setBridgeInfo(DexEncodedMethod method, BridgeInfo bridgeInfo);

  void setClassInlinerMethodConstraint(
      ProgramMethod method, ClassInlinerMethodConstraint classInlinerConstraint);

  void setEnumUnboxerMethodClassification(
      ProgramMethod method, EnumUnboxerMethodClassification enumUnboxerMethodClassification);

  void unsetEnumUnboxerMethodClassification(ProgramMethod method);

  void setInstanceInitializerInfoCollection(
      DexEncodedMethod method, InstanceInitializerInfoCollection instanceInitializerInfoCollection);

  void setInitializerEnablingJavaVmAssertions(DexEncodedMethod method);

  void setNonNullParamOrThrow(DexEncodedMethod method, BitSet facts);

  void setNonNullParamOnNormalExits(DexEncodedMethod method, BitSet facts);

  void setSimpleInliningConstraint(ProgramMethod method, SimpleInliningConstraint constraint);

  void classInitializerMayBePostponed(DexEncodedMethod method);

  void setUnusedArguments(ProgramMethod method, BitSet unusedArguments);
}
