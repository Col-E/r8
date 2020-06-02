// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.optimize.classinliner.ClassInlinerEligibilityInfo;
import com.android.tools.r8.ir.optimize.info.ParameterUsagesInfo.ParameterUsage;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import java.util.BitSet;
import java.util.Set;

public abstract class MethodOptimizationInfo {

  enum InlinePreference {
    NeverInline,
    ForceInline,
    Default
  }

  public abstract boolean isDefaultMethodOptimizationInfo();

  public abstract boolean isUpdatableMethodOptimizationInfo();

  public abstract UpdatableMethodOptimizationInfo asUpdatableMethodOptimizationInfo();

  public abstract boolean cannotBeKept();

  public abstract boolean classInitializerMayBePostponed();

  public abstract TypeElement getDynamicUpperBoundType();

  public final TypeElement getDynamicUpperBoundTypeOrElse(TypeElement orElse) {
    TypeElement dynamicUpperBoundType = getDynamicUpperBoundType();
    return dynamicUpperBoundType != null ? dynamicUpperBoundType : orElse;
  }

  public abstract ClassTypeElement getDynamicLowerBoundType();

  public abstract ParameterUsage getParameterUsages(int parameter);

  public final boolean hasNonNullParamOrThrow() {
    return getNonNullParamOrThrow() != null;
  }

  public abstract BitSet getNonNullParamOrThrow();

  public abstract BitSet getNonNullParamOnNormalExits();

  public abstract boolean hasBeenInlinedIntoSingleCallSite();

  public abstract boolean isReachabilitySensitive();

  public abstract boolean returnsArgument();

  public abstract int getReturnedArgument();

  public abstract boolean neverReturnsNormally();

  public abstract BridgeInfo getBridgeInfo();

  public abstract ClassInlinerEligibilityInfo getClassInlinerEligibility();

  public abstract Set<DexType> getInitializedClassesOnNormalExit();

  public abstract InstanceInitializerInfo getInstanceInitializerInfo();

  public abstract boolean isInitializerEnablingJavaVmAssertions();

  public abstract AbstractValue getAbstractReturnValue();

  public abstract boolean forceInline();

  public abstract boolean neverInline();

  public abstract boolean checksNullReceiverBeforeAnySideEffect();

  public abstract boolean triggersClassInitBeforeAnySideEffect();

  public abstract boolean mayHaveSideEffects();

  public abstract boolean returnValueOnlyDependsOnArguments();

  public abstract boolean returnValueHasBeenPropagated();

  public abstract UpdatableMethodOptimizationInfo mutableCopy();
}
